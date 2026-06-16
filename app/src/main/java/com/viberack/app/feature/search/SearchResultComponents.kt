package com.viberack.app.feature.search

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.core.ui.ComponentInfoDialog
import com.viberack.app.core.ui.MaterialListCard
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.SearchInventoryRecord
import java.io.File

@Composable
internal fun SearchResultCard(
    item: SearchResultUiModel,
    showTotalQuantity: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val imageModel = item.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
    val secondarySummary = searchResultSecondarySummary(item)

    MaterialListCard(
        title = item.name?.takeIf { it.isNotBlank() }
            ?: item.mpn?.takeIf { it.isNotBlank() }
            ?: item.partNumber,
        subtitle = listOfNotNull(item.brand, item.packageName, item.category).joinToString(" · "),
        secondarySummary = secondarySummary,
        sourceText = item.sourceUrl,
        imageModel = imageModel,
        imageContentDescription = item.name ?: item.partNumber,
        placeholderText = item.partNumber,
        onClick = onClick,
        detailContent = {
            if (showTotalQuantity) {
                Text(
                    text = stringResource(R.string.search_total_quantity, displaySearchQuantity(item.totalQuantity)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        bottomContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.locations.forEach { location ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(parseSearchColor(location.colorHex))
                        )
                        Text(
                            text = formatSearchResultLocationLabel(location),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.search_location_quantity, displaySearchQuantity(location.quantity)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun SearchRecordPickerDialog(
    item: SearchResultUiModel,
    onDismiss: () -> Unit,
    onSelect: (SearchInventoryRecord) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = item.name ?: item.mpn ?: item.partNumber) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_locations),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        item.records,
                        key = { record ->
                            "${record.containerType}-${record.inventoryItemId}-${record.stockItemId ?: 0}-${record.slotId ?: 0}"
                        }
                    ) { record ->
                        SearchLocationRecordCard(
                            record = record,
                            selected = false,
                            onClick = { onSelect(record) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun SearchContainerRecordDialog(
    record: SearchInventoryRecord,
    onFindByLight: (SearchInventoryRecord, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = record.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
    var actionError by remember(record.stockItemId, record.inventoryItemId) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(record.stockItemId, record.inventoryItemId) { mutableStateOf(false) }
    val containerTypeLabel = record.containerType.searchLabel()
    val locationLabel = formatSearchRecordLocationLabel(record)
    val quantityText = displaySearchQuantity(record.quantity)
    val firstPropertyRows = listOf(
        stringResource(R.string.inbound_component_number) to record.partNumber,
        stringResource(R.string.inbound_component_brand) to (record.brand ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_package) to (record.packageName ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_category) to (record.category ?: stringResource(R.string.inbound_field_empty))
    )
    val secondPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_name) to (record.name ?: stringResource(R.string.inbound_field_empty)))
        record.specifications.forEach { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isNotEmpty() && normalizedValue.isNotEmpty()) {
                add(normalizedKey to normalizedValue)
            }
        }
        record.description?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.inbound_component_description) to it)
        }
        add(stringResource(R.string.search_container_type) to containerTypeLabel)
        add(stringResource(R.string.search_container_location) to locationLabel)
        record.slotNumber?.let { slotNumber ->
            add(stringResource(R.string.search_container_slot) to slotNumber.toString())
        }
        record.containerMacAddress?.takeIf { it.isNotBlank() }?.let { macAddress ->
            add(stringResource(R.string.search_container_mac) to macAddress)
        }
        add(stringResource(R.string.inventory_quantity_label) to quantityText)
    }

    ComponentInfoDialog(
        title = record.name ?: record.mpn ?: record.partNumber,
        imageModel = imageModel,
        contentDescription = record.name ?: record.partNumber,
        fallbackText = record.partNumber,
        firstPropertyRows = firstPropertyRows,
        secondPropertyRows = secondPropertyRows,
        onDismiss = onDismiss,
        dismissButtons = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.canFindByLight) {
                    TextButton(
                        onClick = {
                            actionError = null
                            isSubmitting = true
                            onFindByLight(record) { error ->
                                isSubmitting = false
                                actionError = error
                                if (error == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.search_find_light_started,
                                            record.slotNumber ?: 0
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        Text(text = stringResource(R.string.search_find_light))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting
                ) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        },
        extraContent = {
            Text(
                text = stringResource(R.string.search_container_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun SearchLocationRecordCard(
    record: SearchInventoryRecord,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSearchRecordLocationLabel(record),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.search_location_quantity, displaySearchQuantity(record.quantity)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

internal fun searchResultSecondarySummary(item: SearchResultUiModel): String? {
    return item.specifications.values
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

@Composable
private fun parseSearchColor(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return runCatching {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    }.getOrDefault(fallback)
}

private fun formatSearchLocationLabel(code: String, displayName: String?): String {
    val normalizedName = displayName?.trim().orEmpty()
    return if (normalizedName.isNotEmpty() && normalizedName != code) {
        "$code:$normalizedName"
    } else {
        code
    }
}

internal fun BomSearchRowUiModel.hasPickTargets(): Boolean {
    return matchedResults.any { result ->
        result.records.any(SearchInventoryRecord::canFindByLight)
    }
}

@Composable
private fun formatSearchResultLocationLabel(location: SearchResultLocationUiModel): String {
    val baseLabel = formatSearchLocationLabel(location.code, location.displayName)
    return when (location.containerType) {
        ContainerType.LEGACY_LOCATION -> baseLabel
        ContainerType.BOX -> location.slotNumber
            ?.let { "$baseLabel / ${location.containerType.searchLabel()} $it" }
            ?: baseLabel
        ContainerType.SMART_CHASSIS -> location.slotNumber
            ?.let { "$baseLabel / ${location.containerType.searchLabel()} $it" }
            ?: baseLabel
    }
}

@Composable
private fun formatSearchRecordLocationLabel(record: SearchInventoryRecord): String {
    val baseLabel = formatSearchLocationLabel(record.locationCode, record.locationDisplayName)
    return when (record.containerType) {
        ContainerType.LEGACY_LOCATION -> baseLabel
        ContainerType.BOX -> record.slotCode
            ?.takeIf { it.isNotBlank() && !baseLabel.contains(it) }
            ?.let { "$baseLabel / $it" }
            ?: baseLabel
        ContainerType.SMART_CHASSIS -> record.slotNumber
            ?.let { "$baseLabel / ${record.containerType.searchLabel()} $it" }
            ?: baseLabel
    }
}

@Composable
private fun ContainerType.searchLabel(): String {
    return stringResource(
        when (this) {
            ContainerType.LEGACY_LOCATION -> R.string.search_container_type_location
            ContainerType.BOX -> R.string.search_container_type_box
            ContainerType.SMART_CHASSIS -> R.string.search_container_type_smart_chassis
        }
    )
}
