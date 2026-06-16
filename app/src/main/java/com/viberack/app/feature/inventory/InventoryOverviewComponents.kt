package com.viberack.app.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation

@Composable
internal fun AddLocationDialog(
    errorMessage: String?,
    existingLocations: List<StorageLocation>,
    recentLocationColors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit,
    onAddRecentLocationColor: (String) -> Unit
) {
    var locationCode by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }
    var showColorWheelDialog by remember { mutableStateOf(false) }
    var codeFieldHadFocus by remember { mutableStateOf(false) }
    var codeValidationRequested by remember { mutableStateOf(false) }
    val locationCodeFormatError = stringResource(R.string.inventory_error_location_code_format)
    val locationCodeExistsError = stringResource(R.string.inventory_error_location_code_exists)
    val codeValidationError = validateLocationCodeInput(
        code = locationCode,
        existingLocations = existingLocations,
        currentLocationId = null,
        formatError = locationCodeFormatError,
        existsError = locationCodeExistsError
    ).takeIf { codeValidationRequested }
    val quickColors = remember(recentLocationColors) { buildLocationQuickColors(recentLocationColors) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.inventory_add_location)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = locationCode,
                    onValueChange = {
                        locationCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }
                        if (codeValidationRequested && validateLocationCodeInput(
                                code = locationCode,
                                existingLocations = existingLocations,
                                currentLocationId = null,
                                formatError = locationCodeFormatError,
                                existsError = locationCodeExistsError
                            ) == null
                        ) {
                            codeValidationRequested = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                codeFieldHadFocus = true
                            } else if (codeFieldHadFocus) {
                                codeValidationRequested = true
                            }
                        },
                    label = { Text(text = stringResource(R.string.inventory_add_location_label)) },
                    isError = codeValidationError != null,
                    supportingText = {
                        Text(text = codeValidationError ?: stringResource(R.string.inventory_add_location_hint))
                    }
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.inventory_location_name)) }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.inventory_location_color),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        quickColors.forEach { color ->
                            ColorQuickButton(
                                colorHex = color,
                                selected = colorHex == color,
                                onClick = { colorHex = color }
                            )
                        }
                        IconButton(
                            onClick = { showColorWheelDialog = true },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.inventory_location_pick_color)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(parseColorOrDefault(colorHex))
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        )
                    }
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val validationError = validateLocationCodeInput(
                    code = locationCode,
                    existingLocations = existingLocations,
                    currentLocationId = null,
                    formatError = locationCodeFormatError,
                    existsError = locationCodeExistsError
                )
                if (validationError != null) {
                    codeValidationRequested = true
                    return@Button
                }
                onConfirm(locationCode, displayName, colorHex)
            }) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )

    if (showColorWheelDialog) {
        ColorWheelDialog(
            initialColorHex = colorHex,
            onDismiss = { showColorWheelDialog = false },
            onConfirm = { pickedColor ->
                onAddRecentLocationColor(pickedColor)
                colorHex = pickedColor
                showColorWheelDialog = false
            }
        )
    }
}

@Composable
internal fun StockLocationCard(
    cell: StockLocationCell,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = parseColorOrDefault(cell.colorHex)
    val contentColor = contentColorForLocationCard(backgroundColor)
    val secondaryContentColor = contentColor.copy(alpha = 0.82f)

    Card(
        modifier = Modifier
            .widthIn(min = 120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = cell.displayName ?: cell.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                softWrap = false,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(contentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(
                text = stringResource(R.string.inventory_cell_summary, cell.code, cell.inventoryItemCount),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryContentColor,
                softWrap = false,
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}

@Composable
internal fun SelectableLocationCard(
    cell: StockLocationCell,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = parseColorOrDefault(cell.colorHex)
    val contentColor = contentColorForLocationCard(backgroundColor)
    val secondaryContentColor = contentColor.copy(alpha = 0.82f)

    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 120.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = cell.displayName ?: cell.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                softWrap = false,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(contentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(
                text = stringResource(R.string.inventory_cell_summary, cell.code, cell.inventoryItemCount),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryContentColor,
                softWrap = false,
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}
