package com.example.lcsc_android_erp.feature.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.ui.ComponentInfoDialog
import com.example.lcsc_android_erp.core.ui.LocationPickerDialog
import com.example.lcsc_android_erp.core.ui.LocationPickerOption
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryItemManageDialog(
    item: LocationInventoryItem,
    currentLocation: StockLocationCell,
    availableLocations: List<StockLocationCell>,
    onUpdateQuantity: (Long, Int, (String?) -> Unit) -> Unit,
    onTransfer: (Long, String, (String?) -> Unit) -> Unit,
    onDelete: (Long, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = item.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
        ?: item.imageUrl?.takeIf { it.isNotBlank() }
    var quantityText by remember(item.inventoryItemId) { mutableStateOf(item.quantity.toString()) }
    var showTransferPicker by remember(item.inventoryItemId) { mutableStateOf(false) }
    var selectedTransferLocationCode by remember(item.inventoryItemId, currentLocation.code) {
        mutableStateOf(
            availableLocations
                .firstOrNull { it.id == currentLocation.id }
                ?.code
                ?: availableLocations.firstOrNull()?.code
                ?: currentLocation.code
        )
    }
    var actionError by remember(item.inventoryItemId) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(item.inventoryItemId) { mutableStateOf(false) }
    val firstPropertyRows = listOf(
        stringResource(R.string.inbound_component_number) to item.partNumber,
        stringResource(R.string.inbound_component_brand) to (item.brand ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_package) to (item.packageName ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_category) to (item.category ?: stringResource(R.string.inbound_field_empty))
    )
    val secondPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_name) to (item.name ?: stringResource(R.string.inbound_field_empty)))
        item.specifications.forEach { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isNotEmpty() && normalizedValue.isNotEmpty()) {
                add(normalizedKey to normalizedValue)
            }
        }
        item.description?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.inbound_component_description) to it)
        }
        add(
            stringResource(R.string.inventory_current_location) to (
                currentLocation.displayName?.takeIf { it.isNotBlank() && it != currentLocation.code }
                    ?.let { "${currentLocation.code}:${it}" }
                    ?: currentLocation.code
                )
        )
        add(stringResource(R.string.inventory_inbound_time) to formatManageDateTime(item.lastInboundAt))
        add(stringResource(R.string.inventory_quantity_label) to displayManageQuantity(item.quantity, context.getString(R.string.inventory_unknown_quantity)))
    }

    ComponentInfoDialog(
        title = item.name ?: item.mpn ?: item.partNumber,
        imageModel = imageModel,
        contentDescription = item.name,
        fallbackText = item.partNumber,
        firstPropertyRows = firstPropertyRows,
        secondPropertyRows = secondPropertyRows,
        onDismiss = onDismiss,
        dismissButtons = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting
                ) {
                    Text(text = stringResource(R.string.common_close))
                }
                TextButton(
                    onClick = {
                        actionError = null
                        if (availableLocations.isEmpty()) {
                            actionError = context.getString(R.string.inventory_no_available_locations)
                            return@TextButton
                        }
                        showTransferPicker = true
                    },
                    enabled = !isSubmitting && availableLocations.isNotEmpty()
                ) {
                    Text(text = stringResource(R.string.inventory_transfer_location))
                }
                TextButton(
                    onClick = {
                        actionError = null
                        isSubmitting = true
                        onDelete(item.inventoryItemId) { error ->
                            isSubmitting = false
                            actionError = error
                            if (error == null) {
                                onDismiss()
                            }
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    Text(
                        text = stringResource(R.string.inventory_delete_item),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        OutlinedTextField(
            value = quantityText,
            onValueChange = { quantityText = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.inventory_edit_quantity)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(
            onClick = {
                actionError = null
                val quantity = quantityText.toIntOrNull()
                if (quantity == null) {
                    actionError = context.getString(R.string.inventory_edit_quantity_error)
                    return@Button
                }
                isSubmitting = true
                onUpdateQuantity(item.inventoryItemId, quantity) { error ->
                    isSubmitting = false
                    actionError = error
                    if (error == null) {
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        ) {
            Text(text = stringResource(R.string.inventory_save_quantity))
        }
        actionError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showTransferPicker) {
        LocationPickerDialog(
            title = stringResource(R.string.inventory_select_target_location),
            options = availableLocations.map { cell ->
                LocationPickerOption(
                    code = cell.code,
                    displayName = cell.displayName,
                    colorHex = cell.colorHex
                )
            },
            selectedCode = selectedTransferLocationCode,
            currentOption = LocationPickerOption(
                code = currentLocation.code,
                displayName = currentLocation.displayName,
                colorHex = currentLocation.colorHex
            ),
            onSelect = { code ->
                selectedTransferLocationCode = code
                actionError = null
                if (code == currentLocation.code) {
                    return@LocationPickerDialog
                }
                isSubmitting = true
                onTransfer(item.inventoryItemId, code) { error ->
                    isSubmitting = false
                    actionError = error
                    if (error == null) {
                        showTransferPicker = false
                        onDismiss()
                    }
                }
            },
            onDismiss = { showTransferPicker = false }
        )
    }
}

private fun displayManageQuantity(quantity: Int, unknownLabel: String): String {
    return if (quantity == 0) unknownLabel else quantity.toString()
}

private fun formatManageDateTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "-"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
