package com.example.lcsc_android_erp.feature.search

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.ui.ComponentInfoDialog
import com.example.lcsc_android_erp.core.ui.MaterialListCard
import com.example.lcsc_android_erp.core.ui.performCopyFeedback
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocation
import com.example.lcsc_android_erp.feature.inbound.ComponentDetailTable
import com.example.lcsc_android_erp.feature.inbound.ExistingStockReminderCard
import com.example.lcsc_android_erp.feature.inbound.MaterialInboundDialog
import com.example.lcsc_android_erp.feature.inventory.InventoryItemManageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchRoute(
    onViewInventoryItem: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as LcscApplication).appContainer
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(appContainer)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onModeChange = viewModel::updateMode,
        onQueryChange = viewModel::updateQuery,
        onNextPage = viewModel::goToNextPage,
        onBomFilterChange = viewModel::updateBomFilter,
        onIgnoreBomEntry = viewModel::ignoreBomEntry,
        onBindBomEntry = viewModel::bindBomEntry,
        onLookupBomDirectInbound = viewModel::lookupBomDirectInbound,
        onAddBomInbound = viewModel::addBomInbound,
        onAssignBomToLayer = viewModel::assignBomEntryToEmptyLayer,
        onUpdateInventoryItemQuantity = viewModel::updateInventoryItemQuantity,
        onUpdateInventoryItemSource = viewModel::updateInventoryItemSource,
        onTransferInventoryItem = viewModel::transferInventoryItem,
        onDeleteInventoryItem = viewModel::deleteInventoryItem,
        onFindSmartChassisRecord = viewModel::findSmartChassisRecord,
        onStartBomPickToLight = viewModel::startBomPickToLight,
        onCancelBomPickToLight = viewModel::cancelBomPickToLight,
        onBomImportStarted = viewModel::startBomImport,
        onBomImportSuccess = viewModel::onBomImportSuccess,
        onBomImportFailed = viewModel::onBomImportFailed,
        onViewInventoryItem = onViewInventoryItem,
        modifier = modifier
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onModeChange: (SearchMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onNextPage: () -> Unit,
    onBomFilterChange: (BomMatchFilter) -> Unit,
    onIgnoreBomEntry: (BomSearchEntry) -> Unit,
    onBindBomEntry: (BomSearchEntry, String) -> Unit,
    onLookupBomDirectInbound: (String, (BomDirectInboundLookupResult) -> Unit) -> Unit,
    onAddBomInbound: (ComponentDetail, Int, String, (String?) -> Unit) -> Unit,
    onAssignBomToLayer: (BomSearchEntry, (BomLayerAssignmentResult) -> Unit) -> Unit,
    onUpdateInventoryItemQuantity: (Long, Int, (String?) -> Unit) -> Unit,
    onUpdateInventoryItemSource: (Long, String?, (String?) -> Unit) -> Unit,
    onTransferInventoryItem: (Long, String, (String?) -> Unit) -> Unit,
    onDeleteInventoryItem: (Long, (String?) -> Unit) -> Unit,
    onFindSmartChassisRecord: (SearchInventoryRecord, (String?) -> Unit) -> Unit,
    onStartBomPickToLight: () -> Unit,
    onCancelBomPickToLight: () -> Unit,
    onBomImportStarted: () -> Unit,
    onBomImportSuccess: (ParsedBomDocument) -> Unit,
    onBomImportFailed: (String) -> Unit,
    onViewInventoryItem: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedSearchResult by remember { mutableStateOf<SearchResultUiModel?>(null) }
    var selectedSearchRecord by remember { mutableStateOf<SearchInventoryRecord?>(null) }
    var bindingTargetEntry by remember { mutableStateOf<BomSearchEntry?>(null) }
    var directInboundTargetEntry by remember { mutableStateOf<BomSearchEntry?>(null) }
    var assigningBomEntryKey by remember { mutableStateOf<String?>(null) }
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
                listState.firstVisibleItemScrollOffset > 600
        }
    }
    val shouldLoadNextManualPage by remember(uiState.mode, uiState.currentPage, uiState.pageCount) {
        derivedStateOf {
            if (uiState.mode != SearchMode.Manual || uiState.currentPage >= uiState.pageCount) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?: return@derivedStateOf false
                lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3
            }
        }
    }
    val bomPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onBomImportFailed(context.getString(R.string.search_bom_import_cancelled))
            return@rememberLauncherForActivityResult
        }
        onBomImportStarted()
        scope.launch {
            runCatching {
                resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val result = runCatching {
                parseBomDocument(
                    context = context,
                    resolver = resolver,
                    uri = uri
                )
            }
            result.onSuccess(onBomImportSuccess).onFailure { error ->
                onBomImportFailed(
                    error.message ?: context.getString(R.string.search_bom_import_failed)
                )
            }
        }
    }

    LaunchedEffect(shouldLoadNextManualPage) {
        if (shouldLoadNextManualPage) {
            onNextPage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.search_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SearchModeTabs(
                    mode = uiState.mode,
                    onModeChange = onModeChange
                )
            }

            when (uiState.mode) {
                SearchMode.Manual -> {
                    item {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.search_keyword_label)) },
                            placeholder = { Text(text = stringResource(R.string.search_keyword_placeholder)) },
                            singleLine = true
                        )
                    }

                    item {
                        Text(
                            text = stringResource(
                                R.string.search_result_summary,
                                uiState.inventoryRecordCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.results.isEmpty()) {
                        item {
                            MessageCard(text = stringResource(R.string.search_empty))
                        }
                    } else {
                        items(uiState.pagedResults, key = { it.partNumber + (it.mpn ?: "") }) { item ->
                            SearchResultCard(
                                item = item,
                                showTotalQuantity = false,
                                onClick = {
                                    if (item.records.size == 1) {
                                        selectedSearchRecord = item.records.first()
                                    } else {
                                        selectedSearchResult = item
                                    }
                                }
                            )
                        }
                    }
                }

                SearchMode.Bom -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    bomPickerLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.search_bom_pick_file))
                            }
                            uiState.bomFileName?.let { fileName ->
                                Text(
                                    text = stringResource(R.string.search_bom_file_name, fileName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (uiState.isParsingBom) {
                        item {
                            Card {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Text(text = stringResource(R.string.search_bom_parsing))
                                }
                            }
                        }
                    }

                    uiState.bomError?.let { message ->
                        item {
                            MessageCard(text = message)
                        }
                    }

                    if (!uiState.isParsingBom && uiState.bomFileName == null && uiState.bomError == null) {
                        item {
                            MessageCard(text = stringResource(R.string.search_bom_empty_hint))
                        }
                    }

                    if (uiState.bomRows.isNotEmpty()) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.bomFilter == BomMatchFilter.All,
                                    onClick = { onBomFilterChange(BomMatchFilter.All) },
                                    label = { Text(text = stringResource(R.string.search_bom_filter_all)) }
                                )
                                FilterChip(
                                    selected = uiState.bomFilter == BomMatchFilter.Matched,
                                    onClick = {
                                        onBomFilterChange(
                                            if (uiState.bomFilter == BomMatchFilter.Matched) {
                                                BomMatchFilter.All
                                            } else {
                                                BomMatchFilter.Matched
                                            }
                                        )
                                    },
                                    label = { Text(text = stringResource(R.string.search_bom_filter_matched)) }
                                )
                                FilterChip(
                                    selected = uiState.bomFilter == BomMatchFilter.Unmatched,
                                    onClick = {
                                        onBomFilterChange(
                                            if (uiState.bomFilter == BomMatchFilter.Unmatched) {
                                                BomMatchFilter.All
                                            } else {
                                                BomMatchFilter.Unmatched
                                            }
                                        )
                                    },
                                    label = { Text(text = stringResource(R.string.search_bom_filter_unmatched)) }
                                )
                            }
                        }

                        item {
                            Text(
                                text = stringResource(
                                    R.string.search_bom_summary,
                                    uiState.bomRows.size,
                                    uiState.bomMatchedCount
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item {
                            BomPickControlCard(
                                session = uiState.bomPickSession,
                                message = uiState.bomPickMessage,
                                isBusy = uiState.isBomPickBusy,
                                hasTargets = uiState.bomRows.any(BomSearchRowUiModel::hasPickTargets),
                                onStart = onStartBomPickToLight,
                                onCancel = onCancelBomPickToLight
                            )
                        }

                        items(uiState.bomRows, key = { it.entry.rowNumber + "|" + (it.entry.supplierPart ?: it.entry.manufacturerPart ?: "") }) { row ->
                            val rowActionKey = bomEntryActionKey(row.entry)
                            BomSearchRowCard(
                                row = row,
                                isAssigningToLayer = assigningBomEntryKey == rowActionKey,
                                onIgnore = { onIgnoreBomEntry(row.entry) },
                                onBind = { bindingTargetEntry = row.entry },
                                onResultClick = { record -> selectedSearchRecord = record },
                                onResultGroupClick = { result -> selectedSearchResult = result },
                                onAssignToEmptyLayer = {
                                    if (uiState.emptyBoxLayers.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.search_bom_assign_no_empty_layer),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        assigningBomEntryKey = rowActionKey
                                        onAssignBomToLayer(row.entry) { result ->
                                            assigningBomEntryKey = null
                                            val error = result.errorMessage
                                            if (error != null) {
                                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                            } else {
                                                val assignedLayer = result.assignedLayer
                                                val partNumber = result.partNumber
                                                if (partNumber != null) {
                                                    onBindBomEntry(row.entry, partNumber)
                                                }
                                                if (assignedLayer != null && partNumber != null) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.search_bom_assign_success,
                                                            partNumber,
                                                            assignedLayer.positionCode
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onDirectInbound = {
                                    if (!row.entry.supplierPart.isNullOrBlank()) {
                                        directInboundTargetEntry = row.entry
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        if (showScrollToTop) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(52.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = null
                )
            }
        }
    }

    selectedSearchResult?.let { item ->
        SearchRecordPickerDialog(
            item = item,
            onDismiss = { selectedSearchResult = null },
            onSelect = { record ->
                selectedSearchRecord = record
                selectedSearchResult = null
            }
        )
    }

    selectedSearchRecord?.let { record ->
        if (record.isLegacyEditable) {
            InventoryItemManageDialog(
                item = record.toLocationInventoryItem(),
                currentLocation = record.toStockLocationCell(),
                availableLocations = uiState.locations.map(StorageLocation::toStockLocationCell),
                onUpdateQuantity = onUpdateInventoryItemQuantity,
                onUpdateSource = onUpdateInventoryItemSource,
                onTransfer = onTransferInventoryItem,
                onDelete = onDeleteInventoryItem,
                onDismiss = { selectedSearchRecord = null }
            )
        } else {
            SearchContainerRecordDialog(
                record = record,
                onFindByLight = onFindSmartChassisRecord,
                onDismiss = { selectedSearchRecord = null }
            )
        }
    }

    bindingTargetEntry?.let { entry ->
        BomBindingDialog(
            entry = entry,
            inventoryResults = uiState.allInventoryResults,
            onDismiss = { bindingTargetEntry = null },
            onBind = { partNumber ->
                onBindBomEntry(entry, partNumber)
                bindingTargetEntry = null
            }
        )
    }

    directInboundTargetEntry?.let { entry ->
        BomDirectInboundDialog(
            entry = entry,
            locations = uiState.locations,
            defaultLocationCode = uiState.defaultLocationCode,
            onLookup = onLookupBomDirectInbound,
            onConfirmInbound = onAddBomInbound,
            onMatchUpdated = { partNumber ->
                onBindBomEntry(entry, partNumber)
            },
            onDismiss = { directInboundTargetEntry = null },
            onViewInventoryItem = { locationCode, partNumber ->
                directInboundTargetEntry = null
                onViewInventoryItem(locationCode, partNumber)
            }
        )
    }
}

@Composable
private fun SearchModeTabs(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit
) {
    val modes = listOf(SearchMode.Manual, SearchMode.Bom)
    TabRow(selectedTabIndex = modes.indexOf(mode).coerceAtLeast(0)) {
        modes.forEach { currentMode ->
            Tab(
                selected = currentMode == mode,
                onClick = { onModeChange(currentMode) },
                text = {
                    Text(
                        text = stringResource(
                            if (currentMode == SearchMode.Manual) {
                                R.string.search_mode_manual
                            } else {
                                R.string.search_mode_bom
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun BomSearchRowCard(
    row: BomSearchRowUiModel,
    isAssigningToLayer: Boolean,
    onIgnore: () -> Unit,
    onBind: () -> Unit,
    onResultClick: (SearchInventoryRecord) -> Unit,
    onResultGroupClick: (SearchResultUiModel) -> Unit,
    onAssignToEmptyLayer: () -> Unit,
    onDirectInbound: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.search_bom_row_title,
                    row.entry.rowNumber,
                    row.entry.quantity ?: 0
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BomInfoLine(label = stringResource(R.string.search_bom_supplier_part), value = row.entry.supplierPart)
                BomInfoLine(label = stringResource(R.string.search_bom_comment), value = row.entry.comment)
                BomInfoLine(label = stringResource(R.string.search_bom_designator), value = row.entry.designator)
                BomInfoLine(label = stringResource(R.string.search_bom_footprint), value = row.entry.footprint)
                BomInfoLine(label = stringResource(R.string.search_bom_value), value = row.entry.value)
                BomInfoLine(label = stringResource(R.string.search_bom_manufacturer_part), value = row.entry.manufacturerPart)
                BomInfoLine(label = stringResource(R.string.search_bom_manufacturer), value = row.entry.manufacturer)
            }
            if (!row.entry.supplierPart.isNullOrBlank() && row.assignedLayers.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onAssignToEmptyLayer,
                        enabled = !isAssigningToLayer
                    ) {
                        Text(
                            text = stringResource(
                                if (isAssigningToLayer) {
                                    R.string.search_bom_assigning_to_layer
                                } else {
                                    R.string.search_bom_assign_to_box_layer
                                }
                            )
                        )
                    }
                }
            }
            if (row.assignedLayers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.search_bom_assigned_layer_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    row.assignedLayers.forEach { layer ->
                        Text(
                            text = stringResource(
                                R.string.search_bom_assigned_layer,
                                layer.positionCode,
                                layer.partNumber.orEmpty()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (row.matchedResults.isEmpty() && row.assignedLayers.isEmpty()) {
                MessageCard(text = stringResource(R.string.search_bom_unmatched))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!row.entry.supplierPart.isNullOrBlank()) {
                        TextButton(onClick = onDirectInbound) {
                            Text(text = stringResource(R.string.search_bom_direct_inbound))
                        }
                    }
                    TextButton(onClick = onIgnore) {
                        Text(text = stringResource(R.string.search_bom_ignore))
                    }
                    Button(onClick = onBind) {
                        Text(text = stringResource(R.string.search_bom_bind_match))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.search_bom_match_title, row.matchedResults.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.matchedResults.forEach { result ->
                        SearchResultCard(
                            item = result,
                            showTotalQuantity = false,
                            onClick = {
                                if (result.records.size == 1) {
                                    onResultClick(result.records.first())
                                } else {
                                    onResultGroupClick(result)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BomDirectInboundDialog(
    entry: BomSearchEntry,
    locations: List<StorageLocation>,
    defaultLocationCode: String?,
    onLookup: (String, (BomDirectInboundLookupResult) -> Unit) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String, (String?) -> Unit) -> Unit,
    onMatchUpdated: (String) -> Unit,
    onDismiss: () -> Unit,
    onViewInventoryItem: (String, String) -> Unit
) {
    val context = LocalContext.current
    var lookupResult by remember(entry) { mutableStateOf(BomDirectInboundLookupResult()) }
    var isLoading by remember(entry) { mutableStateOf(true) }
    val initialQuantityText = remember(entry) { entry.quantity?.toString() ?: "0" }
    var quantityText by remember(entry) { mutableStateOf(initialQuantityText) }
    var selectedLocationCode by remember(entry, locations, defaultLocationCode) {
        mutableStateOf(
            defaultLocationCode
                ?.takeIf { default -> locations.any { it.code == default } }
                ?: locations.firstOrNull()?.code
                ?: ""
        )
    }
    var actionError by remember(entry) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(entry) { mutableStateOf(false) }

    LaunchedEffect(entry) {
        isLoading = true
        lookupResult = BomDirectInboundLookupResult()
        actionError = null
        onLookup(entry.supplierPart.orEmpty()) { result ->
            lookupResult = result
            isLoading = false
        }
    }

    MaterialInboundDialog(
        title = stringResource(R.string.search_bom_direct_inbound),
        component = lookupResult.component,
        isLoading = isLoading,
        loadingText = stringResource(R.string.search_bom_direct_inbound_loading),
        errorMessage = actionError ?: lookupResult.errorMessage,
        existingStockLocations = lookupResult.existingStockLocations,
        quantityText = quantityText,
        quantityEditable = true,
        quantityLabel = stringResource(R.string.search_bom_direct_inbound_quantity),
        onQuantityChange = { quantityText = it.filter(Char::isDigit) },
        quantityShowUndo = quantityText != initialQuantityText,
        onQuantityUndo = { quantityText = initialQuantityText },
        selectedLocationCode = selectedLocationCode,
        availableLocations = locations,
        onLocationSelected = { selectedLocationCode = it },
        onDismiss = onDismiss,
        onConfirm = {
            val component = lookupResult.component ?: return@MaterialInboundDialog
            val quantity = quantityText.toIntOrNull()
            if (quantity == null) {
                actionError = context.getString(R.string.search_bom_direct_inbound_quantity_error)
                return@MaterialInboundDialog
            }
            if (selectedLocationCode.isBlank()) {
                actionError = context.getString(R.string.search_bom_direct_inbound_location_error)
                return@MaterialInboundDialog
            }
            isSubmitting = true
            actionError = null
            onConfirmInbound(component, quantity, selectedLocationCode) { error ->
                isSubmitting = false
                actionError = error
                if (error == null) {
                    onMatchUpdated(component.partNumber)
                    Toast.makeText(
                        context,
                        context.getString(R.string.search_bom_direct_inbound_success, component.partNumber),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        },
        confirmEnabled = !isSubmitting && !isLoading && lookupResult.component != null,
        confirmText = stringResource(R.string.search_bom_direct_inbound_confirm),
        onViewExistingStock = {
            val component = lookupResult.component ?: return@MaterialInboundDialog
            val targetLocation = lookupResult.existingStockLocations.firstOrNull()
                ?: return@MaterialInboundDialog
            onViewInventoryItem(targetLocation.locationCode, component.partNumber)
        }
    )
}

@Composable
private fun BomBindingDialog(
    entry: BomSearchEntry,
    inventoryResults: List<SearchResultUiModel>,
    onDismiss: () -> Unit,
    onBind: (String) -> Unit
) {
    var query by remember(entry) { mutableStateOf("") }
    val filteredResults = remember(inventoryResults, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            inventoryResults
        } else {
            inventoryResults.filter { result ->
                buildList {
                    add(result.partNumber)
                    result.name?.let(::add)
                    result.mpn?.let(::add)
                    result.brand?.let(::add)
                    result.packageName?.let(::add)
                    result.category?.let(::add)
                }.any { value ->
                    value.trim().lowercase().contains(normalizedQuery)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = { Text(text = stringResource(R.string.search_bom_bind_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = entry.supplierPart?.takeIf { it.isNotBlank() }
                        ?: entry.manufacturerPart?.takeIf { it.isNotBlank() }
                        ?: entry.comment?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.search_bom_bind_dialog_subtitle_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.search_bom_bind_search_label)) },
                    singleLine = true
                )
                if (filteredResults.isEmpty()) {
                    MessageCard(text = stringResource(R.string.search_bom_bind_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredResults, key = { it.partNumber + (it.mpn ?: "") }) { result ->
                            BomBindingCandidateCard(
                                result = result,
                                onBind = { onBind(result.partNumber) }
                            )
                        }
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
private fun BomBindingCandidateCard(
    result: SearchResultUiModel,
    onBind: () -> Unit
) {
    val imageModel = result.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
    val secondarySummary = searchResultSecondarySummary(result)

    MaterialListCard(
        title = result.name?.takeIf { it.isNotBlank() }
            ?: result.mpn?.takeIf { it.isNotBlank() }
            ?: result.partNumber,
        subtitle = listOfNotNull(result.brand, result.packageName, result.category).joinToString(" · "),
        secondarySummary = secondarySummary,
        sourceText = result.sourceUrl,
        imageModel = imageModel,
        imageContentDescription = result.name ?: result.partNumber,
        placeholderText = result.partNumber,
        detailContent = {
            Text(
                text = stringResource(R.string.search_part_number, result.partNumber),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        bottomContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onBind) {
                    Text(text = stringResource(R.string.search_bom_bind_confirm))
                }
            }
        }
    )
}

@Composable
private fun BomInfoLine(
    label: String,
    value: String?
) {
    val normalizedValue = value?.trim().orEmpty()
    if (normalizedValue.isEmpty()) {
        return
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(normalizedValue))
                    performCopyFeedback(context, hapticFeedback)
                    Toast.makeText(
                        context,
                        context.getString(R.string.common_copied, normalizedValue),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = normalizedValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MessageCard(text: String) {
    Card {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SearchResultCard(
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
private fun SearchRecordPickerDialog(
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
private fun SearchContainerRecordDialog(
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

private fun searchResultSecondarySummary(item: SearchResultUiModel): String? {
    return item.specifications.values
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

@Composable
private fun BomPickControlCard(
    session: BomPickSessionUiModel?,
    message: String?,
    isBusy: Boolean,
    hasTargets: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.search_bom_pick_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = session?.let {
                    stringResource(
                        R.string.search_bom_pick_active_summary,
                        it.slotCount,
                        it.groups.size
                    )
                } ?: stringResource(R.string.search_bom_pick_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session?.groups?.forEach { group ->
                Text(
                    text = stringResource(
                        R.string.search_bom_pick_group,
                        group.containerCode,
                        group.slots.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            message?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (session == null) {
                    Button(
                        onClick = onStart,
                        enabled = hasTargets && !isBusy
                    ) {
                        Text(text = stringResource(R.string.search_bom_pick_start))
                    }
                } else {
                    TextButton(
                        onClick = onCancel,
                        enabled = !isBusy
                    ) {
                        Text(text = stringResource(R.string.search_bom_pick_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun parseSearchColor(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return runCatching {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    }.getOrDefault(fallback)
}

private suspend fun parseBomDocument(
    context: android.content.Context,
    resolver: ContentResolver,
    uri: Uri
): ParsedBomDocument = withContext(Dispatchers.IO) {
    val fileName = queryDisplayName(resolver, uri) ?: "BOM.xlsx"
    val stream = resolver.openInputStream(uri) ?: error(context.getString(R.string.search_bom_read_failed))
    BomSpreadsheetParser.parse(
        context = context,
        fileName = fileName,
        inputStream = stream
    )
}

private fun queryDisplayName(
    resolver: ContentResolver,
    uri: Uri
): String? {
    return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
}

private fun formatSearchLocationLabel(code: String, displayName: String?): String {
    val normalizedName = displayName?.trim().orEmpty()
    return if (normalizedName.isNotEmpty() && normalizedName != code) {
        "$code:$normalizedName"
    } else {
        code
    }
}

private fun BomSearchRowUiModel.hasPickTargets(): Boolean {
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

private fun bomEntryActionKey(entry: BomSearchEntry): String {
    return listOf(
        entry.rowNumber,
        entry.supplierPart.orEmpty(),
        entry.manufacturerPart.orEmpty(),
        entry.designator.orEmpty()
    ).joinToString("|")
}

@Composable
private fun displaySearchQuantity(quantity: Int): String {
    return quantity.toString()
}

private fun SearchInventoryRecord.toLocationInventoryItem(): LocationInventoryItem {
    return LocationInventoryItem(
        inventoryItemId = inventoryItemId,
        componentId = componentId,
        partNumber = partNumber,
        mpn = mpn,
        name = name,
        brand = brand,
        packageName = packageName,
        category = category,
        description = description,
        sourceUrl = sourceUrl,
        specifications = specifications,
        imageLocalPath = imageLocalPath,
        imageUrl = null,
        quantity = quantity,
        lastInboundAt = 0L
    )
}

private fun SearchInventoryRecord.toStockLocationCell(): StockLocationCell {
    return StockLocationCell(
        id = locationId,
        code = locationCode,
        displayName = locationDisplayName,
        colorHex = locationColorHex,
        sortMode = "",
        remark = null,
        inventoryItemCount = 0,
        totalQuantity = quantity
    )
}

private fun StorageLocation.toStockLocationCell(): StockLocationCell {
    return StockLocationCell(
        id = id,
        code = code,
        displayName = displayName,
        colorHex = colorHex,
        sortMode = sortMode,
        remark = remark,
        inventoryItemCount = 0,
        totalQuantity = 0
    )
}
