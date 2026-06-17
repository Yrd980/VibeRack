package com.viberack.app.feature.search

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
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.ui.ComponentInfoDialog
import com.viberack.app.core.ui.MaterialListCard
import com.viberack.app.core.ui.performCopyFeedback
import com.viberack.app.domain.model.SearchInventoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchRoute(
    onViewInventoryItem: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
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
                            BomSearchRowCard(
                                row = row,
                                onIgnore = { onIgnoreBomEntry(row.entry) },
                                onBind = { bindingTargetEntry = row.entry },
                                onResultClick = { record -> selectedSearchRecord = record },
                                onResultGroupClick = { result -> selectedSearchResult = result },
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
        SearchContainerRecordDialog(
            record = record,
            onFindByLight = onFindSmartChassisRecord,
            onDismiss = { selectedSearchRecord = null }
        )
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
    onIgnore: () -> Unit,
    onBind: () -> Unit,
    onResultClick: (SearchInventoryRecord) -> Unit,
    onResultGroupClick: (SearchResultUiModel) -> Unit
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
            if (row.matchedResults.isEmpty()) {
                MessageCard(text = stringResource(R.string.search_bom_unmatched))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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
fun MessageCard(text: String) {
    Card {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
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

@Composable
internal fun displaySearchQuantity(quantity: Int): String {
    return quantity.toString()
}
