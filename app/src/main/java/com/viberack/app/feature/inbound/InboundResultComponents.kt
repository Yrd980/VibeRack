package com.viberack.app.feature.inbound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.core.ui.MaterialListCard
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.StorageLocation

@Composable
internal fun ManualSearchResultCard(
    component: ComponentDetail,
    hasExistingStock: Boolean,
    onPrintClick: () -> Unit,
    onInboundClick: () -> Unit
) {
    val secondarySummary = remember(component) { inboundComponentSecondarySummary(component) }

    MaterialListCard(
        title = component.name ?: component.mpn ?: component.partNumber,
        subtitle = listOfNotNull(component.brand, component.packageName, component.category).joinToString(" · "),
        secondarySummary = secondarySummary,
        sourceText = component.productUrl,
        imageModel = component.imageUrl?.takeIf { it.isNotBlank() },
        imageContentDescription = component.name ?: component.partNumber,
        placeholderText = component.partNumber,
        onClick = onInboundClick,
        titleTrailing = {
            if (hasExistingStock) {
                Text(
                    text = stringResource(R.string.inbound_existing_stock_badge),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        },
        detailContent = {
            ManualSearchMetaLine(
                label = stringResource(R.string.inbound_component_number),
                value = component.partNumber
            )
        },
        bottomContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrintClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.inbound_manual_print_qr))
                }
                Button(
                    onClick = onInboundClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.inbound_manual_confirm_action))
                }
            }
        }
    )
}

@Composable
private fun ManualSearchMetaLine(
    label: String,
    value: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun inboundComponentSecondarySummary(component: ComponentDetail): String? {
    val preferredKeys = listOf("电阻类型", "阻值", "容值", "精度", "功率")
    val summary = buildList {
        preferredKeys.forEach { key ->
            component.specifications[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
        component.specifications
            .filterKeys { it !in preferredKeys }
            .toSortedMap()
            .values
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .forEach(::add)
    }.distinct().joinToString(" · ")

    return summary.takeIf { it.isNotBlank() }
}

@Composable
internal fun InboundConfirmDialog(
    state: InboundDialogState,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
    onViewExistingStock: (String, String) -> Unit,
    onEdit: (ComponentDetail) -> Unit
) {
    val initialQuantityText = remember(state) {
        if (state.quantityEditable) {
            state.initialQuantity
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        } else {
            state.initialQuantity.coerceAtLeast(1).toString()
        }
    }
    var quantityText by remember(state) { mutableStateOf(initialQuantityText) }
    var locationText by remember(state) { mutableStateOf(state.initialLocation) }
    val hasExistingStock = state.existingStockLocations.isNotEmpty()
    val confirmedQuantity = if (state.quantityEditable) {
        if (quantityText.isBlank()) {
            0
        } else {
            quantityText.toIntOrNull()?.takeIf { it >= 0 }
        }
    } else {
        state.initialQuantity.coerceAtLeast(1)
    }

    MaterialInboundDialog(
        title = state.title,
        component = state.component,
        isLoading = false,
        loadingText = stringResource(R.string.inbound_component_loading),
        errorMessage = null,
        existingStockLocations = if (hasExistingStock) state.existingStockLocations else emptyList(),
        quantityText = quantityText,
        quantityEditable = state.quantityEditable,
        quantityLabel = stringResource(
            if (hasExistingStock && state.quantityEditable) {
                R.string.inbound_quantity_increment_label
            } else {
                R.string.inbound_quantity_label
            }
        ),
        onQuantityChange = { quantityText = it.filter(Char::isDigit) },
        quantityShowUndo = quantityText != initialQuantityText,
        onQuantityUndo = { quantityText = initialQuantityText },
        selectedLocationCode = locationText.ifBlank { state.initialLocation },
        availableLocations = state.availableLocations,
        onLocationSelected = { locationText = it },
        onDismiss = onDismiss,
        onEdit = { onEdit(state.component) },
        onConfirm = {
            confirmedQuantity?.let { quantity ->
                onConfirm(quantity, locationText.ifBlank { "A1" })
            }
        },
        confirmEnabled = confirmedQuantity != null,
        confirmText = stringResource(R.string.common_confirm),
        onViewExistingStock = {
            val targetLocation = state.existingStockLocations.firstOrNull() ?: return@MaterialInboundDialog
            onViewExistingStock(targetLocation.locationCode, state.component.partNumber)
        }
    )
}

@Composable
private fun InboundLocationCard(
    location: StorageLocation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(parseInboundLocationColorOrDefault(location.colorHex))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = location.displayName?.takeIf { it.isNotBlank() } ?: location.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = location.code,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun parseInboundLocationColorOrDefault(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return try {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
