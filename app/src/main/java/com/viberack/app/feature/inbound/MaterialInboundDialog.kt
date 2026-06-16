package com.viberack.app.feature.inbound

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import coil3.compose.AsyncImage
import com.viberack.app.R
import com.viberack.app.core.ui.LocationPickerDialog
import com.viberack.app.core.ui.LocationPickerOption
import com.viberack.app.core.ui.QuantityOutlinedTextField
import com.viberack.app.core.ui.performCopyFeedback
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.StorageLocation

@Composable
fun MaterialInboundDialog(
    title: String,
    component: ComponentDetail?,
    isLoading: Boolean,
    loadingText: String,
    errorMessage: String?,
    existingStockLocations: List<ExistingStockLocation>,
    quantityText: String,
    quantityEditable: Boolean,
    quantityLabel: String,
    onQuantityChange: (String) -> Unit,
    quantityShowUndo: Boolean = false,
    onQuantityUndo: (() -> Unit)? = null,
    selectedLocationCode: String,
    availableLocations: List<StorageLocation>,
    onLocationSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    confirmText: String,
    locationPickerEnabled: Boolean = true,
    selectedLocationLabelOverride: String? = null,
    onEdit: (() -> Unit)? = null,
    leadingActionText: String? = null,
    onLeadingAction: (() -> Unit)? = null,
    onViewExistingStock: (() -> Unit)? = null
) {
    var showLocationPicker by remember(title, selectedLocationCode, availableLocations) { mutableStateOf(false) }
    var pendingLocationCode by remember(title, selectedLocationCode, availableLocations) {
        mutableStateOf(selectedLocationCode)
    }
    val selectedLocationLabel = selectedLocationLabelOverride
        ?: availableLocations.firstOrNull { it.code == selectedLocationCode }?.let {
            materialInboundFormatLocationLabel(it.code, it.displayName)
        }
        ?: selectedLocationCode
    val actionTextStyle = MaterialTheme.typography.labelLarge.copy(lineHeight = 16.sp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        isLoading -> {
                            Card {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                    Text(text = loadingText)
                                }
                            }
                        }

                        else -> {
                            errorMessage?.let { MaterialInboundMessageCard(text = it) }
                            component?.let { currentComponent ->
                                existingStockLocations
                                    .takeIf { it.isNotEmpty() }
                                    ?.let {
                                        ExistingStockReminderCard(
                                            existingStockLocations = it,
                                            onViewItem = onViewExistingStock
                                        )
                                    }
                                MaterialInboundComponentDetail(component = currentComponent)
                                if (quantityEditable) {
                                    QuantityOutlinedTextField(
                                        value = quantityText,
                                        onValueChange = onQuantityChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = quantityLabel,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        onDecrease = {
                                            val current = quantityText.toIntOrNull() ?: 0
                                            onQuantityChange((current - 1).coerceAtLeast(0).toString())
                                        },
                                        decreaseContentDescription = stringResource(R.string.common_decrease),
                                        onIncrease = {
                                            val current = quantityText.toIntOrNull()
                                            onQuantityChange(((current ?: 0) + 1).toString())
                                        },
                                        increaseContentDescription = stringResource(R.string.common_increase),
                                        showUndo = quantityShowUndo,
                                        onUndo = onQuantityUndo,
                                        undoContentDescription = stringResource(R.string.common_undo)
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = quantityText,
                                        onValueChange = {},
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(text = quantityLabel) },
                                        readOnly = true
                                    )
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (onEdit != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(
                                    enabled = component != null && !isLoading,
                                    onClick = onEdit
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.common_edit)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedLocationLabel.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    pendingLocationCode = selectedLocationCode
                                    showLocationPicker = true
                                },
                                enabled = locationPickerEnabled && availableLocations.isNotEmpty() && component != null && !isLoading
                            ) {
                                Text(
                                    text = selectedLocationLabel,
                                    style = actionTextStyle
                                )
                            }
                        }
                        if (!leadingActionText.isNullOrBlank() && onLeadingAction != null) {
                            TextButton(onClick = onLeadingAction) {
                                Text(
                                    text = leadingActionText,
                                    style = actionTextStyle
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.common_cancel)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    color = if (confirmEnabled && !isLoading && component != null) {
                                        Color(0xFF2E7D32)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable(
                                    enabled = confirmEnabled && !isLoading && component != null,
                                    onClick = onConfirm
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = confirmText,
                                tint = if (confirmEnabled && !isLoading && component != null) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            title = stringResource(R.string.inbound_pick_location),
            options = availableLocations.map { location ->
                LocationPickerOption(
                    code = location.code,
                    displayName = location.displayName,
                    colorHex = location.colorHex
                )
            },
            selectedCode = pendingLocationCode,
            currentOption = availableLocations
                .firstOrNull { it.code == selectedLocationCode }
                ?.let { location ->
                    LocationPickerOption(
                        code = location.code,
                        displayName = location.displayName,
                        colorHex = location.colorHex
                    )
                },
            onSelect = { code ->
                pendingLocationCode = code
                onLocationSelected(code)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }
}

@Composable
private fun MaterialInboundComponentDetail(
    component: ComponentDetail
) {
    val density = LocalDensity.current
    var firstPropertyHeightPx by remember(component.partNumber) { mutableStateOf(0) }
    val firstPropertyRows = listOf(
        stringResource(R.string.inbound_component_number) to component.partNumber,
        stringResource(R.string.inbound_component_brand) to (component.brand ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_package) to (component.packageName ?: stringResource(R.string.inbound_field_empty)),
        stringResource(R.string.inbound_component_category) to (component.category ?: stringResource(R.string.inbound_field_empty))
    )
    val secondPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_name) to (component.name ?: stringResource(R.string.inbound_field_empty)))
        component.specifications.forEach { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isNotEmpty() && normalizedValue.isNotEmpty()) {
                add(normalizedKey to normalizedValue)
            }
        }
        add(stringResource(R.string.inbound_component_price) to (component.price?.let { "¥$it" } ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_description) to (component.description ?: stringResource(R.string.inbound_field_empty)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            MaterialInboundImageCard(
                component = component,
                imageHeight = with(density) {
                    if (firstPropertyHeightPx > 0) {
                        firstPropertyHeightPx.toDp()
                    } else {
                        168.dp
                    }
                }
            )
            MaterialInboundFirstPropertyCard(
                rows = firstPropertyRows,
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { size ->
                        firstPropertyHeightPx = size.height
                    }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        MaterialInboundKeyValueCard(rows = secondPropertyRows)
    }
}

@Composable
private fun MaterialInboundImageCard(
    component: ComponentDetail,
    imageHeight: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = Modifier.width(168.dp)
    ) {
        val imageUrl = component.imageUrl?.takeIf { it.isNotBlank() }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = component.name ?: component.partNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = component.partNumber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MaterialInboundFirstPropertyCard(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                MaterialInboundFirstPropertyCell(
                    label = label,
                    value = value,
                    modifier = Modifier.fillMaxWidth()
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MaterialInboundFirstPropertyCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(value))
                    performCopyFeedback(context, hapticFeedback)
                    Toast.makeText(
                        context,
                        context.getString(R.string.common_copied, value),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
    }
}

@Composable
private fun MaterialInboundKeyValueCard(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                MaterialInboundKeyValueRow(
                    label = label,
                    value = value
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MaterialInboundKeyValueRow(
    label: String,
    value: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(value))
                    performCopyFeedback(context, hapticFeedback)
                    Toast.makeText(
                        context,
                        context.getString(R.string.common_copied, value),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MaterialInboundMessageCard(text: String) {
    Card {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun materialInboundFormatLocationLabel(code: String, displayName: String?): String {
    val normalizedName = displayName?.trim().orEmpty()
    return if (normalizedName.isNotEmpty() && normalizedName != code) {
        "$code:$normalizedName"
    } else {
        code
    }
}
