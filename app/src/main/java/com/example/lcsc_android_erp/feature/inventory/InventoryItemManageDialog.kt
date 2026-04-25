package com.example.lcsc_android_erp.feature.inventory

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.lcsc_android_erp.core.ui.QuantityOutlinedTextField
import com.example.lcsc_android_erp.core.ui.SourceOutlinedTextField
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

@Composable
fun InventoryItemManageDialog(
    item: LocationInventoryItem,
    currentLocation: StockLocationCell,
    availableLocations: List<StockLocationCell>,
    onUpdateQuantity: (Long, Int, (String?) -> Unit) -> Unit,
    onUpdateSource: (Long, String?, (String?) -> Unit) -> Unit,
    onTransfer: (Long, String, (String?) -> Unit) -> Unit,
    onDelete: (Long, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val originalQuantityText = item.quantity.toString()
    val originalSourceText = item.sourceUrl.orEmpty()
    val imageModel = item.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
        ?: item.imageUrl?.takeIf { it.isNotBlank() }
    var quantityText by remember(item.inventoryItemId) { mutableStateOf(originalQuantityText) }
    var sourceText by remember(item.inventoryItemId) { mutableStateOf(originalSourceText) }
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
    val quantityChanged = quantityText != originalQuantityText
    val sourceChanged = sourceText != originalSourceText
    val openableSourceUrl = remember(sourceText) { extractOpenableSourceUrl(sourceText) }
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
                    onClick = {
                        actionError = null
                        val quantity = quantityText.toIntOrNull()
                        val normalizedSourceText = sourceText.trim().takeIf { it.isNotEmpty() }
                        when {
                            !quantityChanged && !sourceChanged -> onDismiss()
                            quantity == null -> {
                                actionError = context.getString(R.string.inventory_edit_quantity_error)
                            }
                            else -> {
                                isSubmitting = true
                                onUpdateQuantity(item.inventoryItemId, quantity) { error ->
                                    if (error != null) {
                                        isSubmitting = false
                                        actionError = error
                                        return@onUpdateQuantity
                                    }
                                    onUpdateSource(item.inventoryItemId, normalizedSourceText) { sourceError ->
                                        isSubmitting = false
                                        actionError = sourceError
                                        if (sourceError == null) {
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                        }
                    },
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
        QuantityOutlinedTextField(
            value = quantityText,
            onValueChange = {
                quantityText = it.filter(Char::isDigit)
                actionError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.inventory_edit_quantity),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onDecrease = {
                val current = quantityText.toIntOrNull() ?: 0
                quantityText = (current - 1).coerceAtLeast(0).toString()
                actionError = null
            },
            decreaseContentDescription = stringResource(R.string.common_decrease),
            onIncrease = {
                val current = quantityText.toIntOrNull() ?: 0
                quantityText = (current + 1).toString()
                actionError = null
            },
            increaseContentDescription = stringResource(R.string.common_increase),
            showUndo = quantityChanged,
            onUndo = {
                quantityText = originalQuantityText
                actionError = null
            },
            undoContentDescription = stringResource(R.string.common_undo),
            enabled = !isSubmitting
        )
        SourceOutlinedTextField(
            value = sourceText,
            onValueChange = {
                sourceText = it
                actionError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.inventory_source_label),
            showUndo = sourceChanged,
            onUndo = {
                sourceText = originalSourceText
                actionError = null
            },
            undoContentDescription = stringResource(R.string.common_undo),
            onValueBlurTransform = ::normalizeSourceValue,
            showOpen = openableSourceUrl != null,
            onOpen = {
                if (openableSourceUrl == null) {
                    return@SourceOutlinedTextField
                }
                runCatching {
                    openSourceUrl(context, openableSourceUrl)
                }.onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.inventory_open_source_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            openContentDescription = stringResource(R.string.inventory_open_source),
            enabled = !isSubmitting
        )
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

private fun extractOpenableSourceUrl(value: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty()) {
        return null
    }
    val urlRegex = Regex("""(?i)\bhttps?://[^\s"”」】]+""")
    return urlRegex.find(normalized)?.value?.trim()
}

private fun normalizeSourceValue(value: String): String {
    val normalized = value.trim()
    return extractOpenableSourceUrl(normalized) ?: normalized
}

private fun openSourceUrl(context: android.content.Context, rawUrl: String) {
    val normalizedUrl = rawUrl.trim()
    val browserUri = normalizedUrl.toUri()
    val intent = Intent(Intent.ACTION_VIEW, browserUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching {
        context.startActivity(intent)
    }.isSuccess
    if (!launched) {
        throw IllegalStateException("No activity can handle source url: $normalizedUrl")
    }
}
