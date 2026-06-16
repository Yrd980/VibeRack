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



internal fun groupLocationRows(cells: List<StockLocationCell>): List<Pair<String, List<StockLocationCell>>> {
    return cells
        .sortedBy { LegacyLocationCode(it.code) }
        .groupBy { LegacyLocationCode(it.code).rowLabel }
        .toList()
        .sortedBy { (rowLabel, _) -> rowLabel }
}
