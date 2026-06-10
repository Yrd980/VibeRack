package com.example.lcsc_android_erp.feature.inventory

import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.nfc.NfcLabelPayloadCodec
import com.example.lcsc_android_erp.core.printer.PrinterConnectionState
import com.example.lcsc_android_erp.core.datastore.UserPreferences
import com.example.lcsc_android_erp.core.printer.PrinterManager
import com.example.lcsc_android_erp.core.ui.LocationPickerDialog
import com.example.lcsc_android_erp.core.ui.LocationPickerOption
import com.example.lcsc_android_erp.core.ui.performCopyFeedback
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocation
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import com.example.lcsc_android_erp.feature.inbound.ExistingStockReminderCard
import com.example.lcsc_android_erp.feature.inbound.MaterialInboundDialog
import com.example.lcsc_android_erp.feature.inbound.ScannerCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun InventoryRoute(
    openRequest: InventoryOpenRequest? = null,
    openRequestSignal: Int = 0,
    resetToOverviewSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as LcscApplication).appContainer
    val viewModel: InventoryViewModel = viewModel(
        factory = InventoryViewModel.factory(appContainer)
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by appContainer.userPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )

    LaunchedEffect(resetToOverviewSignal) {
        viewModel.dismissLocationDetail()
    }

    LaunchedEffect(openRequestSignal) {
        openRequest?.let { request ->
            val locationCode = request.locationCode
            val partNumber = request.partNumber
            when {
                locationCode != null && partNumber != null -> {
                    viewModel.openInventoryItem(locationCode, partNumber)
                }

                locationCode != null -> {
                    viewModel.openInventoryLocation(locationCode)
                }

                partNumber != null -> {
                    viewModel.openFirstInventoryItem(partNumber)
                }
            }
        }
    }

    InventoryScreen(
        modifier = modifier,
        uiState = uiState.value,
        printerManager = appContainer.printerManagerForType(preferences.printerType),
        onLocationSelected = viewModel::onLocationSelected,
        onDismissLocationDetail = viewModel::dismissLocationDetail,
        onOpenLocationSettings = viewModel::openLocationSettings,
        onCloseLocationSettings = viewModel::closeLocationSettings,
        onAddLocation = viewModel::addStorageLocation,
        onClearAddLocationError = viewModel::clearAddLocationError,
        onUpdateLocation = viewModel::updateLocation,
        onClearUpdateLocationError = viewModel::clearUpdateLocationError,
        onDeleteLocation = viewModel::deleteLocation,
        onForceDeleteLocation = viewModel::forceDeleteLocation,
        onLookupScannedComponent = viewModel::lookupScannedComponent,
        onAddScannedInbound = viewModel::addScannedInbound,
        onUpdateInventoryItemQuantity = viewModel::updateInventoryItemQuantity,
        onUpdateInventoryItemSource = viewModel::updateInventoryItemSource,
        onTransferInventoryItem = viewModel::transferInventoryItem,
        onDeleteInventoryItem = viewModel::deleteInventoryItem,
        onTransferInventoryItems = viewModel::transferInventoryItems,
        onDeleteInventoryItems = viewModel::deleteInventoryItems,
        onOpenInventoryItem = viewModel::openInventoryItem,
        onOpenInventoryItemHandled = viewModel::clearPendingOpenRequest,
        onAddRecentLocationColor = viewModel::addRecentLocationColor
    )
}

@Composable
fun InventoryScreen(
    uiState: InventoryUiState,
    printerManager: PrinterManager,
    onLocationSelected: (StockLocationCell) -> Unit,
    onDismissLocationDetail: () -> Unit,
    onOpenLocationSettings: (Long) -> Unit,
    onCloseLocationSettings: () -> Unit,
    onAddLocation: (String, String?, String?) -> Unit,
    onClearAddLocationError: () -> Unit,
    onUpdateLocation: (Long, String, String?, String?, String, (String?) -> Unit) -> Unit,
    onClearUpdateLocationError: () -> Unit,
    onDeleteLocation: (Long, (String?) -> Unit) -> Unit,
    onForceDeleteLocation: (Long, (String?) -> Unit) -> Unit,
    onLookupScannedComponent: (String, (InventoryScanLookupResult) -> Unit) -> Unit,
    onAddScannedInbound: (ComponentDetail, Int, String, String?, () -> Unit) -> Unit,
    onUpdateInventoryItemQuantity: (Long, Int, (String?) -> Unit) -> Unit,
    onUpdateInventoryItemSource: (Long, String?, (String?) -> Unit) -> Unit,
    onTransferInventoryItem: (Long, String, (String?) -> Unit) -> Unit,
    onDeleteInventoryItem: (Long, (String?) -> Unit) -> Unit,
    onTransferInventoryItems: (List<Long>, String, (String?) -> Unit) -> Unit,
    onDeleteInventoryItems: (List<Long>, (String?) -> Unit) -> Unit,
    onOpenInventoryItem: (String, String) -> Unit,
    onOpenInventoryItemHandled: () -> Unit,
    onAddRecentLocationColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var addLocationDialogVisible by remember { mutableStateOf(false) }
    var addLocationSubmitted by remember { mutableStateOf(false) }
    var settingsTargetCell by remember { mutableStateOf<StockLocationCell?>(null) }
    val context = LocalContext.current
    val appContainer = (context.applicationContext as LcscApplication).appContainer
    val rows = groupLocationRows(uiState.cells)

    LaunchedEffect(uiState.cells.size, uiState.addLocationError, addLocationSubmitted) {
        if (addLocationSubmitted && uiState.addLocationError == null) {
            addLocationDialogVisible = false
            addLocationSubmitted = false
        } else if (uiState.addLocationError != null) {
            addLocationSubmitted = false
        }
    }

    LaunchedEffect(uiState.addLocationError) {
        uiState.addLocationError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.selectedLocation == null) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.inventory_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(rows, key = { it.first }) { (letter, cells) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = letter.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(16.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                cells.forEach { cell ->
                                    StockLocationCard(
                                        cell = cell,
                                        onClick = { onLocationSelected(cell) },
                                        onLongClick = {
                                            settingsTargetCell = cell
                                            onOpenLocationSettings(cell.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { addLocationDialogVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.inventory_add_location)
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            InventoryLocationDetailScreen(
                uiState = uiState,
                cell = uiState.selectedLocation,
                items = uiState.selectedLocationItems,
                printerManager = printerManager,
                onBack = onDismissLocationDetail,
                onSave = onUpdateLocation,
                onClearUpdateLocationError = onClearUpdateLocationError,
                onDeleteLocation = onDeleteLocation,
                onForceDeleteLocation = onForceDeleteLocation,
                onLookupScannedComponent = onLookupScannedComponent,
                onAddScannedInbound = onAddScannedInbound,
                onUpdateInventoryItemQuantity = onUpdateInventoryItemQuantity,
                onUpdateInventoryItemSource = onUpdateInventoryItemSource,
                onTransferInventoryItem = onTransferInventoryItem,
                onDeleteInventoryItem = onDeleteInventoryItem,
                onTransferInventoryItems = onTransferInventoryItems,
                onDeleteInventoryItems = onDeleteInventoryItems,
                onOpenInventoryItem = onOpenInventoryItem,
                onOpenInventoryItemHandled = onOpenInventoryItemHandled,
                onAddRecentLocationColor = onAddRecentLocationColor,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (addLocationDialogVisible) {
        AddLocationDialog(
            errorMessage = uiState.addLocationError,
            existingLocations = uiState.locations,
            recentLocationColors = uiState.recentLocationColors,
            onDismiss = {
                onClearAddLocationError()
                addLocationDialogVisible = false
                addLocationSubmitted = false
            },
            onConfirm = { code, displayName, colorHex ->
                addLocationSubmitted = true
                onAddLocation(code, displayName, colorHex)
            },
            onAddRecentLocationColor = onAddRecentLocationColor
        )
    }

    settingsTargetCell?.let { cell ->
        LocationSettingsDialog(
            cell = cell,
            errorMessage = uiState.updateLocationError,
            existingLocations = uiState.locations,
            availableSecondaryAttributes = uiState.settingsLocationSortAttributes,
            recentLocationColors = uiState.recentLocationColors,
            onDismiss = {
                onClearUpdateLocationError()
                onCloseLocationSettings()
                settingsTargetCell = null
            },
            onSave = { code, displayName, colorHex, sortMode ->
                onUpdateLocation(cell.id, code, displayName, colorHex, sortMode) { error ->
                    if (error == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.inventory_location_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                        settingsTargetCell = null
                        onCloseLocationSettings()
                        onClearUpdateLocationError()
                    }
                }
            },
            onAddRecentLocationColor = onAddRecentLocationColor,
            onDelete = {
                onDeleteLocation(cell.id) { error ->
                    if (error == null) {
                        settingsTargetCell = null
                        onCloseLocationSettings()
                        onClearUpdateLocationError()
                    }
                }
            },
            onForceDelete = {
                onForceDeleteLocation(cell.id) { error ->
                    if (error == null) {
                        settingsTargetCell = null
                        onCloseLocationSettings()
                        onClearUpdateLocationError()
                    }
                }
            }
        )
    }
}

@Composable
private fun AddLocationDialog(
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
private fun StockLocationCard(
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
private fun SelectableLocationCard(
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

@Composable
private fun InventoryLocationDetailScreen(
    uiState: InventoryUiState,
    cell: StockLocationCell,
    items: List<LocationInventoryItem>,
    printerManager: PrinterManager,
    onBack: () -> Unit,
    onSave: (Long, String, String?, String?, String, (String?) -> Unit) -> Unit,
    onClearUpdateLocationError: () -> Unit,
    onDeleteLocation: (Long, (String?) -> Unit) -> Unit,
    onForceDeleteLocation: (Long, (String?) -> Unit) -> Unit,
    onLookupScannedComponent: (String, (InventoryScanLookupResult) -> Unit) -> Unit,
    onAddScannedInbound: (ComponentDetail, Int, String, String?, () -> Unit) -> Unit,
    onUpdateInventoryItemQuantity: (Long, Int, (String?) -> Unit) -> Unit,
    onUpdateInventoryItemSource: (Long, String?, (String?) -> Unit) -> Unit,
    onTransferInventoryItem: (Long, String, (String?) -> Unit) -> Unit,
    onDeleteInventoryItem: (Long, (String?) -> Unit) -> Unit,
    onTransferInventoryItems: (List<Long>, String, (String?) -> Unit) -> Unit,
    onDeleteInventoryItems: (List<Long>, (String?) -> Unit) -> Unit,
    onOpenInventoryItem: (String, String) -> Unit,
    onOpenInventoryItemHandled: () -> Unit,
    onAddRecentLocationColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as LcscApplication).appContainer
    val coroutineScope = rememberCoroutineScope()
    val printerState by printerManager.state.collectAsStateWithLifecycle()
    var showSettingsDialog by remember(cell.id) { mutableStateOf(false) }
    var showScanAddDialog by remember(cell.id) { mutableStateOf(false) }
    var showPrintLabelDialog by remember(cell.id) { mutableStateOf(false) }
    var locationLabelBitmap by remember(cell.id) { mutableStateOf<Bitmap?>(null) }
    var locationLabelLoading by remember(cell.id) { mutableStateOf(false) }
    var locationLabelSaving by remember(cell.id) { mutableStateOf(false) }
    var locationLabelPrinting by remember(cell.id) { mutableStateOf(false) }
    val headerBadgeColor = parseColorOrDefault(cell.colorHex)
    val headerBadgeContentColor = contentColorForLocationCard(headerBadgeColor)
    var selectedItem by remember(cell.id) {
        mutableStateOf<com.example.lcsc_android_erp.domain.model.LocationInventoryItem?>(null)
    }
    var selectedItemIds by remember(cell.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchTransferPicker by remember(cell.id) { mutableStateOf(false) }
    var showBatchDeleteDialog by remember(cell.id) { mutableStateOf(false) }
    var batchActionError by remember(cell.id) { mutableStateOf<String?>(null) }
    var batchSubmitting by remember(cell.id) { mutableStateOf(false) }
    val targetLocationOptions = remember(uiState.cells) { uiState.cells }
    val targetLocationRows = remember(targetLocationOptions) { groupLocationRows(targetLocationOptions) }
    var selectedBatchTransferLocationCode by remember(cell.id, targetLocationOptions) {
        mutableStateOf(
            targetLocationOptions
                .firstOrNull { it.id == cell.id }
                ?.code
                ?: targetLocationOptions.firstOrNull()?.code.orEmpty()
        )
    }

    LaunchedEffect(items) {
        val validIds = items.mapTo(mutableSetOf()) { it.inventoryItemId }
        selectedItemIds = selectedItemIds.filterTo(linkedSetOf()) { it in validIds }
        if (selectedItem?.inventoryItemId !in validIds) {
            selectedItem = null
        }
    }

    LaunchedEffect(uiState.pendingOpenRequest, cell.code, items) {
        val request = uiState.pendingOpenRequest ?: return@LaunchedEffect
        if (!request.locationCode.equals(cell.code, ignoreCase = true)) {
            return@LaunchedEffect
        }
        val matchedItem = items.firstOrNull {
            it.partNumber.equals(request.partNumber, ignoreCase = true)
        } ?: return@LaunchedEffect
        selectedItemIds = emptySet()
        batchActionError = null
        selectedItem = matchedItem
        onOpenInventoryItemHandled()
    }

    LaunchedEffect(showPrintLabelDialog, cell.id, items.size) {
        if (!showPrintLabelDialog) {
            return@LaunchedEffect
        }
        LocationLabelExporter.createPreviewBitmap(
            context = context,
            cell = cell,
            inventoryCount = items.size
        ).onSuccess { bitmap ->
            locationLabelBitmap = bitmap
        }.onFailure { error ->
            showPrintLabelDialog = false
            Toast.makeText(
                context,
                error.message ?: context.getString(R.string.inventory_location_label_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
        locationLabelLoading = false
    }

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (selectedItemIds.isNotEmpty()) 132.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.common_back))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cell.code,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = headerBadgeContentColor,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(headerBadgeColor)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        cell.displayName?.takeIf { it.isNotBlank() && it != cell.code }?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            showPrintLabelDialog = true
                            locationLabelBitmap = null
                            locationLabelLoading = true
                            locationLabelSaving = false
                            locationLabelPrinting = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = stringResource(R.string.inventory_print_location_label)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.inventory_location_settings)
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.inventory_location_items),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.inventory_location_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(items, key = { it.inventoryItemId }) { item ->
                    LocationInventoryItemCard(
                        item = item,
                        selected = item.inventoryItemId in selectedItemIds,
                        selectionMode = selectedItemIds.isNotEmpty(),
                        onClick = {
                            batchActionError = null
                            if (selectedItemIds.isNotEmpty()) {
                                selectedItemIds = toggleInventorySelection(selectedItemIds, item.inventoryItemId)
                            } else {
                                selectedItem = item
                            }
                        },
                        onLongClick = {
                            batchActionError = null
                            selectedItemIds = toggleInventorySelection(selectedItemIds, item.inventoryItemId)
                        }
                    )
                }
            }
        }

        if (selectedItemIds.isEmpty()) {
            FloatingActionButton(
                onClick = { showScanAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 16.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.inventory_location_scan_add)
                )
            }
        }

        if (selectedItemIds.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.inventory_batch_selected_count, selectedItemIds.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    batchActionError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                batchActionError = null
                                selectedItemIds = emptySet()
                            },
                            enabled = !batchSubmitting
                        ) {
                            Text(text = stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = {
                                batchActionError = null
                                if (targetLocationOptions.isEmpty()) {
                                    batchActionError = context.getString(R.string.inventory_no_available_locations)
                                } else {
                                    showBatchTransferPicker = true
                                }
                            },
                            enabled = !batchSubmitting && targetLocationOptions.isNotEmpty()
                        ) {
                            Text(text = stringResource(R.string.inventory_batch_transfer))
                        }
                        Button(
                            onClick = {
                                batchActionError = null
                                showBatchDeleteDialog = true
                            },
                            enabled = !batchSubmitting
                        ) {
                            Text(text = stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        LocationSettingsDialog(
            cell = cell,
            errorMessage = uiState.updateLocationError,
            existingLocations = uiState.locations,
            availableSecondaryAttributes = supportedSortAttributes(items, cell.sortMode),
            recentLocationColors = uiState.recentLocationColors,
            onDismiss = {
                onClearUpdateLocationError()
                showSettingsDialog = false
            },
            onSave = { code, displayName, colorHex, sortMode ->
                onSave(cell.id, code, displayName, colorHex, sortMode) { error ->
                    if (error == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.inventory_location_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                        showSettingsDialog = false
                        onClearUpdateLocationError()
                    }
                }
            },
            onAddRecentLocationColor = onAddRecentLocationColor,
            onDelete = {
                onDeleteLocation(cell.id) { error ->
                    if (error == null) {
                        showSettingsDialog = false
                        onBack()
                    }
                }
            },
            onForceDelete = {
                onForceDeleteLocation(cell.id) { error ->
                    if (error == null) {
                        showSettingsDialog = false
                        onBack()
                    }
                }
            }
        )
    }

    if (showScanAddDialog) {
        LocationScanAddDialog(
            locationCode = cell.code,
            onDismiss = { showScanAddDialog = false },
            onLookupScannedComponent = onLookupScannedComponent,
            onConfirmInbound = { component, quantity, rawPayload ->
                onAddScannedInbound(component, quantity, cell.code, rawPayload) {
                    showScanAddDialog = false
                }
            },
            onViewExistingStock = { locationCode, partNumber ->
                showScanAddDialog = false
                onOpenInventoryItem(locationCode, partNumber)
            }
        )
    }

    selectedItem?.let { item ->
        InventoryItemManageDialog(
            item = item,
            currentLocation = cell,
            availableLocations = uiState.cells,
            onUpdateQuantity = onUpdateInventoryItemQuantity,
            onUpdateSource = onUpdateInventoryItemSource,
            onTransfer = onTransferInventoryItem,
            onDelete = onDeleteInventoryItem,
            onDismiss = { selectedItem = null }
        )
    }

    if (showBatchTransferPicker) {
        LocationPickerDialog(
            title = stringResource(R.string.inventory_select_target_location),
            options = targetLocationOptions.map { option ->
                LocationPickerOption(
                    code = option.code,
                    displayName = option.displayName,
                    colorHex = option.colorHex
                )
            },
            selectedCode = selectedBatchTransferLocationCode,
            currentOption = LocationPickerOption(
                code = cell.code,
                displayName = cell.displayName,
                colorHex = cell.colorHex
            ),
            onSelect = { code ->
                selectedBatchTransferLocationCode = code
                batchActionError = null
                if (selectedItemIds.isEmpty() || code.isBlank() || code == cell.code) {
                    return@LocationPickerDialog
                }
                batchSubmitting = true
                onTransferInventoryItems(selectedItemIds.toList(), code) { error ->
                    batchSubmitting = false
                    batchActionError = error
                    if (error == null) {
                        showBatchTransferPicker = false
                        selectedItemIds = emptySet()
                        Toast.makeText(
                            context,
                            context.getString(R.string.inventory_batch_transfer_completed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { showBatchTransferPicker = false }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(text = stringResource(R.string.inventory_batch_delete_title)) },
            text = { Text(text = stringResource(R.string.inventory_batch_delete_message, selectedItemIds.size)) },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteDialog = false },
                    enabled = !batchSubmitting
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        batchActionError = null
                        batchSubmitting = true
                        onDeleteInventoryItems(selectedItemIds.toList()) { error ->
                            batchSubmitting = false
                            batchActionError = error
                            if (error == null) {
                                showBatchDeleteDialog = false
                                selectedItemIds = emptySet()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.inventory_batch_delete_completed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !batchSubmitting && selectedItemIds.isNotEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    if (showPrintLabelDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!locationLabelSaving && !locationLabelPrinting) {
                    showPrintLabelDialog = false
                    locationLabelBitmap = null
                    locationLabelLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(text = stringResource(R.string.inventory_location_label_preview_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (locationLabelLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(text = stringResource(R.string.inventory_location_label_preview_loading))
                        }
                    } else {
                        locationLabelBitmap?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = cell.displayName ?: cell.code,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(
                            text = if (printerState.connectionState == PrinterConnectionState.CONNECTED) {
                                printerState.connectionSummary
                            } else {
                                stringResource(R.string.printer_not_connected)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (locationLabelPrinting || printerState.isPrinting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Text(text = stringResource(R.string.printer_print_in_progress))
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrintLabelDialog = false
                        locationLabelBitmap = null
                        locationLabelLoading = false
                    },
                    enabled = !locationLabelSaving && !locationLabelPrinting
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            appContainer.nfcLabelManager.setPendingWrite(
                                NfcLabelPayloadCodec.locationUri(cell.code)
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.nfc_tap_tag_to_write),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = !locationLabelLoading &&
                            !locationLabelSaving &&
                            !locationLabelPrinting &&
                            locationLabelBitmap != null
                    ) {
                        Text(text = stringResource(R.string.nfc_write_tag))
                    }
                    TextButton(
                        onClick = {
                            val bitmap = locationLabelBitmap ?: return@TextButton
                            locationLabelPrinting = true
                            printerManager.printBitmap(bitmap) { errorMessage ->
                                locationLabelPrinting = false
                                Toast.makeText(
                                    context,
                                    errorMessage ?: context.getString(R.string.printer_print_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !locationLabelLoading &&
                            !locationLabelSaving &&
                            !locationLabelPrinting &&
                            locationLabelBitmap != null &&
                            printerState.connectionState == PrinterConnectionState.CONNECTED &&
                            !printerState.isPrinting
                    ) {
                        Text(text = stringResource(R.string.printer_print_label))
                    }
                    TextButton(
                        onClick = {
                            val bitmap = locationLabelBitmap ?: return@TextButton
                            locationLabelSaving = true
                            coroutineScope.launch {
                                LocationLabelExporter.saveBitmapToGallery(
                                    context = context,
                                    locationCode = cell.code,
                                    bitmap = bitmap
                                ).onSuccess { fileName ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.inventory_location_label_saved, fileName),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPrintLabelDialog = false
                                    locationLabelBitmap = null
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        error.message ?: context.getString(R.string.inventory_location_label_export_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                locationLabelSaving = false
                            }
                        },
                        enabled = !locationLabelLoading &&
                            !locationLabelSaving &&
                            !locationLabelPrinting &&
                            locationLabelBitmap != null
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
                }
            }
        )
    }
}

@Composable
private fun LocationScanAddDialog(
    locationCode: String,
    onDismiss: () -> Unit,
    onLookupScannedComponent: (String, (InventoryScanLookupResult) -> Unit) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String?) -> Unit,
    onViewExistingStock: (String, String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var scannerPaused by rememberSaveable { mutableStateOf(false) }
    var lookupInProgress by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<InventoryScanLookupResult?>(null) }
    val initialQuantityText = remember(scanResult?.rawPayload, scanResult?.quantity) {
        scanResult?.quantity
            ?.takeIf { it > 0 }
            ?.toString()
            .orEmpty()
    }
    var quantityText by remember(scanResult?.rawPayload, scanResult?.quantity) {
        mutableStateOf(initialQuantityText)
    }

    if (lookupInProgress || scanResult != null || scanErrorMessage != null) {
        MaterialInboundDialog(
            title = stringResource(R.string.inventory_location_scan_add),
            component = scanResult?.component,
            isLoading = lookupInProgress,
            loadingText = stringResource(R.string.inbound_component_loading),
            errorMessage = scanErrorMessage,
            existingStockLocations = scanResult?.existingStockLocations.orEmpty(),
            quantityText = quantityText,
            quantityEditable = true,
            quantityLabel = stringResource(R.string.inbound_quantity_label),
            onQuantityChange = { quantityText = it.filter(Char::isDigit) },
            quantityShowUndo = quantityText != initialQuantityText,
            onQuantityUndo = { quantityText = initialQuantityText },
            selectedLocationCode = locationCode,
            availableLocations = emptyList(),
            onLocationSelected = {},
            onDismiss = onDismiss,
            onConfirm = {
                scanResult?.component?.let { component ->
                    onConfirmInbound(
                        component,
                        quantityText.toIntOrNull() ?: 0,
                        scanResult?.rawPayload
                    )
                }
            },
            confirmEnabled = scanResult?.component != null && !lookupInProgress,
            confirmText = stringResource(R.string.common_confirm),
            locationPickerEnabled = false,
            selectedLocationLabelOverride = locationCode,
            onViewExistingStock = {
                val component = scanResult?.component ?: return@MaterialInboundDialog
                val targetLocation = scanResult?.existingStockLocations?.firstOrNull()
                    ?: return@MaterialInboundDialog
                onViewExistingStock(targetLocation.locationCode, component.partNumber)
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.inventory_location_scan_add)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.inventory_location_scan_target, locationCode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScannerCard(
                        hasCameraPermission = hasCameraPermission,
                        scannerPaused = scannerPaused,
                        onQrScanned = { rawText ->
                            if (lookupInProgress || scanResult != null) {
                                return@ScannerCard
                            }
                            scannerPaused = true
                            lookupInProgress = true
                            onLookupScannedComponent(rawText) { result ->
                                lookupInProgress = false
                                if (result.component != null) {
                                    scanResult = result
                                    scanErrorMessage = null
                                } else {
                                    scannerPaused = false
                                    scanResult = null
                                    scanErrorMessage = result.errorMessage
                                }
                            }
                        },
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
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
}

@Composable
private fun ColorQuickButton(
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
private fun LocationSettingsDialog(
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
                                sortPriorities = toggleSortPriority(
                                    current = sortPriorities,
                                    target = StorageLocationSortMode.NAME
                                )
                            }
                        )
                        SortModeOption(
                            label = stringResource(R.string.inventory_sort_mode_quantity),
                            priority = sortPriorities.indexOf(StorageLocationSortMode.QUANTITY).takeIf { it >= 0 }?.plus(1),
                            onClick = {
                                sortPriorities = toggleSortPriority(
                                    current = sortPriorities,
                                    target = StorageLocationSortMode.QUANTITY
                                )
                            }
                        )
                        SortModeOption(
                            label = stringResource(R.string.inventory_sort_mode_inbound_time),
                            priority = sortPriorities.indexOf(StorageLocationSortMode.INBOUND_TIME).takeIf { it >= 0 }?.plus(1),
                            onClick = {
                                sortPriorities = toggleSortPriority(
                                    current = sortPriorities,
                                    target = StorageLocationSortMode.INBOUND_TIME
                                )
                            }
                        )
                        specificationSortOptions.forEach { specificationKey ->
                            val priorityToken = StorageLocationSortMode.bySpecification(specificationKey)
                            SortModeOption(
                                label = specificationKey,
                                priority = sortPriorities.indexOf(priorityToken).takeIf { it >= 0 }?.plus(1),
                                onClick = {
                                    sortPriorities = toggleSortPriority(
                                        current = sortPriorities,
                                        target = priorityToken
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
private fun ColorWheelDialog(
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

@Composable
private fun LocationInventoryItemCard(
    item: LocationInventoryItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imageModel = item.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
        ?: item.imageUrl?.takeIf { it.isNotBlank() }
    val secondarySummary = locationItemSecondarySummary(item)
    val hasTaobaoSource = remember(item.sourceUrl) { inventoryCardHasTaobaoSource(item.sourceUrl) }

    Card(
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onClick()
                    },
                    onLongClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onLongClick()
                    }
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "x${displayQuantity(item.quantity)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (hasTaobaoSource) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset { IntOffset(x = -4.dp.roundToPx(), y = -4.dp.roundToPx()) }
                                .size(14.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(Color(0xFFFF6A00))
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name ?: item.mpn ?: item.partNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listOfNotNull(item.brand, item.packageName, item.category).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    secondarySummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.inventory_item_quantity, displayQuantity(item.quantity)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun inventoryCardHasTaobaoSource(sourceText: String?): Boolean {
    val normalized = sourceText?.trim()?.lowercase().orEmpty()
    return !normalized.startsWith("https://item.szlcsc.com")
}

private fun supportedSortAttributes(
    items: List<LocationInventoryItem>,
    currentSortMode: String
): List<String> {
    return buildList {
        items
            .asSequence()
            .flatMap { item -> item.specifications.keys.asSequence() }
            .map(String::trim)
            .distinct()
            .filter { key -> key.isNotEmpty() && hasMultipleSpecificationValues(items, key) }
            .sorted()
            .forEach(::add)
        StorageLocationSortMode.specificationKey(currentSortMode)
            ?.takeIf { it.isNotBlank() && it !in this }
            ?.let(::add)
    }
}

private fun hasMultipleSpecificationValues(
    items: List<LocationInventoryItem>,
    specificationKey: String
): Boolean {
    return items
        .asSequence()
        .mapNotNull { item ->
            item.specifications[specificationKey]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        .distinct()
        .take(2)
        .count() > 1
}

private fun toggleSortPriority(
    current: List<String>,
    target: String
): List<String> {
    return if (target in current) {
        current.filterNot { it == target }
    } else {
        current + target
    }
}

private fun toggleInventorySelection(
    current: Set<Long>,
    inventoryItemId: Long
): Set<Long> {
    return if (inventoryItemId in current) {
        current - inventoryItemId
    } else {
        current + inventoryItemId
    }
}

private fun buildLocationQuickColors(recentLocationColors: List<String>): List<String> {
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

private fun isValidLocationCode(code: String): Boolean {
    return code.trim().uppercase().matches(Regex("^[A-Z]\\d+$"))
}

private fun validateLocationCodeInput(
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

@Composable
private fun displayQuantity(quantity: Int): String {
    return if (quantity == 0) stringResource(R.string.inventory_unknown_quantity) else quantity.toString()
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "-"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun locationItemSecondarySummary(item: LocationInventoryItem): String? {
    val preferredKeys = listOf("电阻类型", "阻值", "精度", "功率")
    val specificationSummary = buildList {
        preferredKeys.forEach { key ->
            item.specifications[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
        item.specifications
            .filterKeys { it !in preferredKeys }
            .toSortedMap()
            .values
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .forEach(::add)
    }.distinct().joinToString(" · ")

    return specificationSummary.takeIf { it.isNotBlank() }
        ?: item.mpn?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.description?.trim()?.takeIf { it.isNotEmpty() }
}

private fun Offset.toHue(radius: Float): Float {
    val dx = x - radius
    val dy = y - radius
    return (((Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0) + 360.0) % 360.0).toFloat()
}

@Composable
private fun parseColorOrDefault(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return try {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

private fun contentColorForLocationCard(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.62f) {
        Color(0xFF111827)
    } else {
        Color.White
    }
}

private fun locationRowLabel(code: String): String {
    return code.takeWhile { it.isLetter() }.ifBlank { "#" }
}

private fun locationRowIndex(code: String): Int {
    return code.firstOrNull()?.uppercaseChar()?.code ?: Int.MAX_VALUE
}

private fun locationColumnIndex(code: String): Int {
    return code.dropWhile { it.isLetter() }.toIntOrNull() ?: Int.MAX_VALUE
}

private fun groupLocationRows(cells: List<StockLocationCell>): List<Pair<String, List<StockLocationCell>>> {
    return cells
        .sortedWith(compareBy({ locationRowIndex(it.code) }, { locationColumnIndex(it.code) }, { it.code }))
        .groupBy { locationRowLabel(it.code) }
        .toList()
        .sortedBy { (rowLabel, _) -> rowLabel }
}
