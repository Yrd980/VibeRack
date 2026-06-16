package com.viberack.app.feature.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.core.ble.smart.SmartChassisRestorePreview
import com.viberack.app.domain.model.ContainerSlotStock

@Composable
fun SlotList(
    slots: List<ContainerSlotStock>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (slots.isEmpty()) {
            StatusCard(text = stringResource(R.string.containers_no_slots))
        } else {
            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slot.slot.displayName?.takeIf { it.isNotBlank() }
                                ?: slot.slot.slotCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = slot.stockItem?.partNumber
                                ?: stringResource(R.string.containers_slot_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = slot.stockItem?.quantity?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SmartSlotList(
    slots: List<ContainerSlotStock>,
    onFindSlot: (ContainerSlotStock) -> Unit,
    onRequestSlotInbound: (ContainerSlotStock) -> Unit,
    onClearSlot: (ContainerSlotStock) -> Unit,
    onSetSlotQuantity: (ContainerSlotStock, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var quantityEditSlot by remember { mutableStateOf<ContainerSlotStock?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (slots.isEmpty()) {
            StatusCard(text = stringResource(R.string.containers_no_slots))
        } else {
            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slot.slot.displayName?.takeIf { it.isNotBlank() }
                                ?: slot.slot.slotCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = slot.stockItem?.partNumber
                                ?: stringResource(R.string.containers_slot_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    slot.stockItem?.let { stock ->
                        Text(
                            text = stock.quantity.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = { onFindSlot(slot) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(text = stringResource(R.string.containers_slot_find))
                    }
                    OutlinedButton(
                        onClick = { onRequestSlotInbound(slot) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(text = stringResource(R.string.containers_slot_inbound))
                    }
                    if (slot.stockItem != null) {
                        OutlinedButton(
                            onClick = { quantityEditSlot = slot },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(text = stringResource(R.string.containers_slot_set_quantity))
                        }
                        TextButton(onClick = { onClearSlot(slot) }) {
                            Text(text = stringResource(R.string.containers_slot_clear))
                        }
                    }
                }
            }
        }
    }
    quantityEditSlot?.let { slot ->
        SlotQuantityDialog(
            slot = slot,
            onConfirm = { quantity ->
                onSetSlotQuantity(slot, quantity)
                quantityEditSlot = null
            },
            onDismiss = { quantityEditSlot = null }
        )
    }
}

@Composable
fun SlotInboundDialog(
    request: SlotInboundRequest,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var partText by remember(request) { mutableStateOf(request.existingPartNumber.orEmpty()) }
    var quantityText by remember(request) {
        mutableStateOf(request.existingQuantity?.toString() ?: "1")
    }
    val quantity = quantityText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.containers_slot_inbound_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.containers_slot_inbound_target,
                        request.containerCode,
                        request.slotCode
                    )
                )
                OutlinedTextField(
                    value = partText,
                    onValueChange = { partText = it.trim().uppercase() },
                    label = { Text(text = stringResource(R.string.containers_slot_part_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text(text = stringResource(R.string.inbound_quantity_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = partText.isNotBlank() && quantity != null && quantity >= 0,
                onClick = {
                    onConfirm(partText, quantity ?: 0)
                }
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun SlotQuantityDialog(
    slot: ContainerSlotStock,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var quantityText by remember(slot) {
        mutableStateOf(slot.stockItem?.quantity?.toString().orEmpty())
    }
    val quantity = quantityText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.containers_slot_set_quantity_title)) },
        text = {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter(Char::isDigit) },
                label = { Text(text = stringResource(R.string.inbound_quantity_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = quantity != null && quantity >= 0,
                onClick = { onConfirm(quantity ?: 0) }
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun RestorePreviewDialog(
    preview: SmartChassisRestorePreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.containers_restore_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.containers_restore_preview_total, preview.totalSlots))
                Text(text = stringResource(R.string.containers_restore_preview_occupied, preview.occupiedRecords))
                Text(text = stringResource(R.string.containers_restore_preview_empty, preview.emptyRecords))
                Text(text = stringResource(R.string.containers_restore_preview_invalid, preview.invalidRecords))
                Text(text = stringResource(R.string.containers_restore_preview_changed, preview.changedSlots))
                Text(text = "seq ${preview.tableInfo.tableSeq} / crc ${preview.tableInfo.crc16}")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.containers_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun StatusCard(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val background = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
