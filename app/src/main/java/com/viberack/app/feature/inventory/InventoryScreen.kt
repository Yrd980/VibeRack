package com.viberack.app.feature.inventory

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.ContentScale
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.nfc.NfcLabelPayloadCodec
import com.viberack.app.core.printer.PrinterConnectionState
import com.viberack.app.core.datastore.UserPreferences
import com.viberack.app.core.printer.PrinterManager
import com.viberack.app.core.ui.LocationPickerDialog
import com.viberack.app.core.ui.LocationPickerOption
import com.viberack.app.core.ui.performCopyFeedback
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.LegacyLocationCode
import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.domain.stock.LocationStockSortPolicy
import com.viberack.app.feature.inbound.ExistingStockReminderCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val locationStockSortPolicy = LocationStockSortPolicy()

@Composable
fun InventoryRoute(
    openRequest: InventoryOpenRequest? = null,
    openRequestSignal: Int = 0,
    resetToOverviewSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as VibeRackApplication).appContainer
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
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
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
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
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
        mutableStateOf<com.viberack.app.domain.model.LocationInventoryItem?>(null)
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
                                selectedItemIds = locationStockSortPolicy.toggleSelection(selectedItemIds, item.inventoryItemId)
                            } else {
                                selectedItem = item
                            }
                        },
                        onLongClick = {
                            batchActionError = null
                            selectedItemIds = locationStockSortPolicy.toggleSelection(selectedItemIds, item.inventoryItemId)
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
            availableSecondaryAttributes = locationStockSortPolicy.supportedSpecificationAttributes(items, cell.sortMode),
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

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "-"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun groupLocationRows(cells: List<StockLocationCell>): List<Pair<String, List<StockLocationCell>>> {
    return cells
        .sortedBy { LegacyLocationCode(it.code) }
        .groupBy { LegacyLocationCode(it.code).rowLabel }
        .toList()
        .sortedBy { (rowLabel, _) -> rowLabel }
}
