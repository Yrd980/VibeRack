package com.viberack.app.feature.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.domain.model.StorageLocationSortMode
import com.viberack.app.domain.stock.LocationStockSortPolicy
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val locationStockSortPolicy = LocationStockSortPolicy()

@Composable
fun LocationSettingsDialog(
    cell: StockLocationCell,
    errorMessage: String?,
    existingLocations: List<StorageLocation>,
    availableSecondaryAttributes: List<String>,
    recentLocationColors: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String) -> Unit,
    onAddRecentLocationColor: (String) -> Unit,
    onDelete: () -> Unit,
    onForceDelete: () -> Unit
) {
    var code by remember(cell.id) { mutableStateOf(cell.code) }
    var displayName by remember(cell.id) { mutableStateOf(cell.displayName ?: "") }
    var colorHex by remember(cell.id) { mutableStateOf(cell.colorHex ?: "") }
    var sortPriorities by remember(cell.id) { mutableStateOf(StorageLocationSortMode.priorities(cell.sortMode)) }
    var showColorWheelDialog by remember(cell.id) { mutableStateOf(false) }
    var deleteSubmitted by remember(cell.id) { mutableStateOf(false) }
    var deleteBlockedMessage by remember(cell.id) { mutableStateOf<String?>(null) }
    var codeFieldHadFocus by remember(cell.id) { mutableStateOf(false) }
    var codeValidationRequested by remember(cell.id) { mutableStateOf(false) }
    val locationCodeFormatError = stringResource(R.string.inventory_error_location_code_format)
    val locationCodeExistsError = stringResource(R.string.inventory_error_location_code_exists)
    val codeValidationError = validateLocationCodeInput(
        code = code,
        existingLocations = existingLocations,
        currentLocationId = cell.id,
        formatError = locationCodeFormatError,
        existsError = locationCodeExistsError
    ).takeIf { codeValidationRequested }
    val quickColors = remember(recentLocationColors) { buildLocationQuickColors(recentLocationColors) }
    val specificationSortOptions = remember(availableSecondaryAttributes, sortPriorities) {
        buildList {
            availableSecondaryAttributes
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .forEach(::add)
            sortPriorities
                .mapNotNull(StorageLocationSortMode::specificationKey)
                .filter { it.isNotBlank() && it !in this }
                .forEach(::add)
        }
    }

    LaunchedEffect(deleteSubmitted, errorMessage) {
        if (!deleteSubmitted) {
            return@LaunchedEffect
        }
        if (!errorMessage.isNullOrBlank()) {
            deleteBlockedMessage = errorMessage
            deleteSubmitted = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.inventory_location_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }
                        if (codeValidationRequested && validateLocationCodeInput(
                                code = code,
                                existingLocations = existingLocations,
                                currentLocationId = cell.id,
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
                    label = { Text(text = stringResource(R.string.inventory_location_code)) },
                    isError = codeValidationError != null,
                    supportingText = {
                        codeValidationError?.let { Text(text = it) }
                    }
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.inventory_location_name)) }
                )
                LocationColorPickerRow(
                    colorHex = colorHex,
                    quickColors = quickColors,
                    onColorSelected = { colorHex = it },
                    onOpenWheel = { showColorWheelDialog = true }
                )
                LocationSortPriorityPicker(
                    sortPriorities = sortPriorities,
                    specificationSortOptions = specificationSortOptions,
                    onSortPrioritiesChange = { sortPriorities = it }
                )
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
            Button(
                onClick = {
                    val validationError = validateLocationCodeInput(
                        code = code,
                        existingLocations = existingLocations,
                        currentLocationId = cell.id,
                        formatError = locationCodeFormatError,
                        existsError = locationCodeExistsError
                    )
                    if (validationError != null) {
                        codeValidationRequested = true
                        return@Button
                    }
                    onSave(
                        code,
                        displayName,
                        colorHex,
                        StorageLocationSortMode.serialize(sortPriorities)
                    )
                }
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        deleteSubmitted = true
                        onDelete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.common_cancel))
                }
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

    deleteBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteBlockedMessage = null },
            title = { Text(text = stringResource(R.string.inventory_delete_location_blocked_title)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteBlockedMessage = null
                        deleteSubmitted = false
                    }
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteBlockedMessage = null
                        onForceDelete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_force_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@Composable
fun LocationColorPickerRow(
    colorHex: String,
    quickColors: List<String>,
    onColorSelected: (String) -> Unit,
    onOpenWheel: () -> Unit
) {
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
                    onClick = { onColorSelected(color) }
                )
            }
            IconButton(
                onClick = onOpenWheel,
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
}

@Composable
fun ColorQuickButton(
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .clip(MaterialTheme.shapes.small)
            .background(parseColorOrDefault(colorHex))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                } else {
                    Modifier
                }
            )
    ) {
        Text(text = "")
    }
}

@Composable
private fun LocationSortPriorityPicker(
    sortPriorities: List<String>,
    specificationSortOptions: List<String>,
    onSortPrioritiesChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.inventory_location_sort_mode),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortModeOption(
                label = stringResource(R.string.inventory_sort_mode_name),
                priority = sortPriorities.indexOf(StorageLocationSortMode.NAME).takeIf { it >= 0 }?.plus(1),
                onClick = {
                    onSortPrioritiesChange(
                        locationStockSortPolicy.togglePriority(
                            current = sortPriorities,
                            target = StorageLocationSortMode.NAME
                        )
                    )
                }
            )
            SortModeOption(
                label = stringResource(R.string.inventory_sort_mode_quantity),
                priority = sortPriorities.indexOf(StorageLocationSortMode.QUANTITY).takeIf { it >= 0 }?.plus(1),
                onClick = {
                    onSortPrioritiesChange(
                        locationStockSortPolicy.togglePriority(
                            current = sortPriorities,
                            target = StorageLocationSortMode.QUANTITY
                        )
                    )
                }
            )
            SortModeOption(
                label = stringResource(R.string.inventory_sort_mode_inbound_time),
                priority = sortPriorities.indexOf(StorageLocationSortMode.INBOUND_TIME).takeIf { it >= 0 }?.plus(1),
                onClick = {
                    onSortPrioritiesChange(
                        locationStockSortPolicy.togglePriority(
                            current = sortPriorities,
                            target = StorageLocationSortMode.INBOUND_TIME
                        )
                    )
                }
            )
            specificationSortOptions.forEach { specificationKey ->
                val priorityToken = StorageLocationSortMode.bySpecification(specificationKey)
                SortModeOption(
                    label = specificationKey,
                    priority = sortPriorities.indexOf(priorityToken).takeIf { it >= 0 }?.plus(1),
                    onClick = {
                        onSortPrioritiesChange(
                            locationStockSortPolicy.togglePriority(
                                current = sortPriorities,
                                target = priorityToken
                            )
                        )
                    }
                )
            }
            if (specificationSortOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.inventory_sort_mode_no_secondary_attributes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SortModeOption(
    label: String,
    priority: Int?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (priority != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
                .border(
                    1.dp,
                    if (priority != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = priority?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = if (priority != null) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun ColorWheelDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedHue by remember(initialColorHex) { mutableStateOf(initialHue(initialColorHex)) }
    val selectedColor = remember(selectedHue) { Color.hsv(selectedHue, 0.75f, 1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.inventory_location_pick_color)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HueWheel(
                    hue = selectedHue,
                    onHueChange = { selectedHue = it }
                )
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(selectedColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                )
            }
        },
        confirmButton = {
            IconButton(onClick = { onConfirm(colorToHex(selectedColor)) }) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.common_confirm)
                )
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.common_cancel)
                )
            }
        }
    )
}

@Composable
private fun HueWheel(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    val rainbow = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000)
    )
    BoxWithConstraints(
        modifier = Modifier
            .size(220.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onHueChange(offset.toHue(size.width / 2f)) },
                    onDrag = { change, _ ->
                        onHueChange(change.position.toHue(size.width / 2f))
                    }
                )
            }
    ) {
        val density = LocalDensity.current
        val wheelSizePx = with(density) { maxWidth.toPx().coerceAtMost(maxHeight.toPx()) }
        val wheelRadiusPx = wheelSizePx / 2f
        val wheelStrokePx = wheelRadiusPx * 0.28f
        val markerTrackRadiusPx = wheelRadiusPx - wheelStrokePx / 2f
        val markerAngle = Math.toRadians(hue.toDouble() - 90.0)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val strokeWidth = radius * 0.28f
            rotate(degrees = -90f) {
                drawCircle(
                    brush = Brush.sweepGradient(rainbow),
                    radius = radius - strokeWidth / 2f,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (cos(markerAngle) * markerTrackRadiusPx).roundToInt(),
                        y = (sin(markerAngle) * markerTrackRadiusPx).roundToInt()
                    )
                }
                .align(Alignment.Center)
                .size(16.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color.hsv(hue, 0.75f, 1f))
                .border(2.dp, Color.White, MaterialTheme.shapes.small)
        )
    }
}

fun buildLocationQuickColors(recentLocationColors: List<String>): List<String> {
    return buildList {
        add("")
        recentLocationColors
            .map(String::trim)
            .filter { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
            .distinctBy { it.uppercase(Locale.ROOT) }
            .take(5)
            .forEach { add(it.uppercase(Locale.ROOT)) }
    }
}

fun validateLocationCodeInput(
    code: String,
    existingLocations: List<StorageLocation>,
    currentLocationId: Long?,
    formatError: String,
    existsError: String
): String? {
    val normalizedCode = code.trim().uppercase(Locale.ROOT)
    if (!isValidLocationCode(normalizedCode)) {
        return formatError
    }
    val duplicated = existingLocations.any { location ->
        location.id != currentLocationId && location.code.equals(normalizedCode, ignoreCase = true)
    }
    return if (duplicated) existsError else null
}

private fun isValidLocationCode(code: String): Boolean {
    return code.trim().uppercase().matches(Regex("^[A-Z]\\d+$"))
}

private fun initialHue(colorHex: String): Float {
    val hsv = FloatArray(3)
    runCatching {
        android.graphics.Color.colorToHSV(
            android.graphics.Color.parseColor(colorHex.ifBlank { "#B3E5FC" }),
            hsv
        )
    }.getOrElse {
        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#B3E5FC"), hsv)
    }
    return hsv[0]
}

private fun colorToHex(color: Color): String {
    return String.format("#%06X", 0xFFFFFF and color.toArgb())
}

private fun Offset.toHue(radius: Float): Float {
    val dx = x - radius
    val dy = y - radius
    return (((Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0) + 360.0) % 360.0).toFloat()
}

@Composable
fun parseColorOrDefault(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return try {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

fun contentColorForLocationCard(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.62f) {
        Color(0xFF111827)
    } else {
        Color.White
    }
}
