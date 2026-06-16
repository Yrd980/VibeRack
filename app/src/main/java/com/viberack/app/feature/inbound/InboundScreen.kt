package com.viberack.app.feature.inbound

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.nfc.NfcLabelPayloadCodec
import com.viberack.app.core.printer.PrinterManager
import com.viberack.app.core.ui.LocationPickerDialog
import com.viberack.app.core.ui.LocationPickerOption
import com.viberack.app.core.ui.MaterialListCard
import com.viberack.app.core.ui.performCopyFeedback
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.LegacyLocationCode
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.domain.stock.StockInLocationSuggester
import kotlinx.coroutines.launch

private const val MANUAL_INBOUND_DEFAULT_LOCATION_CODE = "A1"
private val stockInLocationSuggester = StockInLocationSuggester()

@Composable
fun InboundRoute(
    onViewInventoryItem: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as VibeRackApplication).appContainer
    val viewModel: InboundViewModel = viewModel(
        factory = InboundViewModel.factory(appContainer)
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by appContainer.userPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = com.viberack.app.core.datastore.UserPreferences()
    )
    InboundScreen(
        modifier = modifier,
        uiState = uiState.value,
        printerManager = appContainer.printerManagerForType(preferences.printerType),
        onQrScanned = viewModel::onQrScanned,
        onContinueScanning = viewModel::clearScanResult,
        onManualSearch = viewModel::searchManual,
        onRefreshNextManualInboundPartNumber = viewModel::refreshNextManualInboundPartNumber,
        onRefreshExistingStock = viewModel::refreshExistingStock,
        onConfirmInbound = { component, quantity, locationCode, sourceType, rawPayload, onCompleted ->
            viewModel.confirmInbound(
                component = component,
                quantity = quantity,
                locationCode = locationCode,
                sourceType = sourceType,
                rawPayload = rawPayload,
                onCompleted = onCompleted
            )
        },
        onViewInventoryItem = onViewInventoryItem,
    )
}

@Composable
fun InboundScreen(
    uiState: InboundUiState,
    printerManager: PrinterManager,
    onQrScanned: (String) -> Unit,
    onContinueScanning: () -> Unit,
    onManualSearch: (String) -> Unit,
    onRefreshNextManualInboundPartNumber: () -> Unit,
    onRefreshExistingStock: (String) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String, String, String?, (String?) -> Unit) -> Unit,
    onViewInventoryItem: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
    val coroutineScope = rememberCoroutineScope()
    val printerState by printerManager.state.collectAsStateWithLifecycle()
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

    var inboundMode by rememberSaveable { mutableStateOf(InboundMode.Scan) }
    var manualKeyword by rememberSaveable { mutableStateOf("") }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualBrand by rememberSaveable { mutableStateOf("") }
    var manualPackageName by rememberSaveable { mutableStateOf("") }
    var manualCategory by rememberSaveable { mutableStateOf("") }
    var manualQuantityText by rememberSaveable { mutableStateOf("") }
    var manualDescription by rememberSaveable { mutableStateOf("") }
    var manualSourceUrl by rememberSaveable { mutableStateOf("") }
    var manualSourceUrlOriginal by rememberSaveable { mutableStateOf("") }
    var manualImageLocalPath by rememberSaveable { mutableStateOf("") }
    var manualImageUrl by rememberSaveable { mutableStateOf("") }
    var manualSpecLines by rememberSaveable { mutableStateOf("") }
    var manualSpecEditorVisible by rememberSaveable { mutableStateOf(false) }
    var manualEntryError by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualImagePicker by rememberSaveable { mutableStateOf(false) }
    var showManualLocationPicker by rememberSaveable { mutableStateOf(false) }
    var manualLocationCode by rememberSaveable { mutableStateOf("") }
    var scannerPaused by rememberSaveable { mutableStateOf(false) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<InboundDialogState?>(null) }
    var qrPreviewComponent by remember { mutableStateOf<ComponentDetail?>(null) }
    var qrPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPreviewLoading by remember { mutableStateOf(false) }
    var qrPreviewSaving by remember { mutableStateOf(false) }
    var qrPreviewPrinting by remember { mutableStateOf(false) }
    val manualPartNumber = uiState.nextManualInboundPartNumber
    val manualImageCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching {
            saveManualInboundBitmap(
                context = context,
                partNumber = manualPartNumber,
                bitmap = bitmap
            )
        }.onSuccess { path ->
            manualImageLocalPath = path
            manualImageUrl = ""
            manualEntryError = null
        }.onFailure { error ->
            Toast.makeText(
                context,
                error.message ?: context.getString(R.string.inbound_manual_entry_image_save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val manualImageGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching {
            saveManualInboundImageUri(
                context = context,
                partNumber = manualPartNumber,
                uri = uri
            )
        }.onSuccess { path ->
            manualImageLocalPath = path
            manualImageUrl = ""
            manualEntryError = null
        }.onFailure { error ->
            Toast.makeText(
                context,
                error.message ?: context.getString(R.string.inbound_manual_entry_image_save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val normalizedManualPartNumber = remember(manualPartNumber) { manualPartNumber.trim().uppercase() }
    val resolvedManualLocationCode = manualLocationCode.ifBlank { MANUAL_INBOUND_DEFAULT_LOCATION_CODE }
    val resolvedManualLocationLabel = uiState.locations
        .firstOrNull { it.code == resolvedManualLocationCode }
        ?.let { location ->
            location.displayName
                ?.takeIf { it.isNotBlank() && it != location.code }
                ?.let { "${location.code}:${it}" }
                ?: location.code
        }
        ?: resolvedManualLocationCode
    val manualEntryComponent = remember(
        manualPartNumber,
        manualName,
        manualBrand,
        manualPackageName,
        manualCategory,
        manualDescription,
        manualSourceUrl,
        manualImageLocalPath,
        manualImageUrl,
        manualSpecLines
    ) {
        buildManualInboundComponent(
            partNumber = manualPartNumber,
            name = manualName,
            brand = manualBrand,
            packageName = manualPackageName,
            category = manualCategory,
            description = manualDescription,
            sourceUrl = manualSourceUrl,
            imageLocalPath = manualImageLocalPath,
            imageUrl = manualImageUrl,
            specificationText = manualSpecLines
        )
    }

    LaunchedEffect(normalizedManualPartNumber) {
        if (normalizedManualPartNumber.isNotBlank()) {
            onRefreshExistingStock(normalizedManualPartNumber)
        }
    }

    val openManualInboundEditor: (ComponentDetail) -> Unit = { component ->
        inboundMode = InboundMode.Manual
        manualSpecEditorVisible = true
        manualName = component.name.orEmpty()
        manualBrand = component.brand.orEmpty()
        manualPackageName = component.packageName.orEmpty()
        manualCategory = component.category.orEmpty()
        manualQuantityText = ""
        manualDescription = component.description.orEmpty()
        manualSourceUrl = ""
        manualSourceUrlOriginal = ""
        manualImageLocalPath = component.imageLocalPath.orEmpty()
        manualImageUrl = component.imageUrl.orEmpty()
        manualSpecLines = component.specifications.entries.joinToString("\n") { (key, value) ->
            "$key=$value"
        }
        manualLocationCode = manualLocationCode.ifBlank { MANUAL_INBOUND_DEFAULT_LOCATION_CODE }
        manualEntryError = null
    }

    LaunchedEffect(inboundMode) {
        if (inboundMode != InboundMode.Scan) {
            torchEnabled = false
        }
    }

    LaunchedEffect(uiState.parsedPayload, uiState.parseError) {
        scannerPaused = uiState.parsedPayload != null || uiState.parseError != null
    }

    LaunchedEffect(uiState.parseError, inboundMode) {
        val errorMessage = uiState.parseError ?: return@LaunchedEffect
        if (inboundMode != InboundMode.Scan) {
            return@LaunchedEffect
        }
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        onContinueScanning()
    }

    LaunchedEffect(uiState.componentLookupError, inboundMode) {
        val errorMessage = uiState.componentLookupError ?: return@LaunchedEffect
        if (inboundMode != InboundMode.Scan) {
            return@LaunchedEffect
        }
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        onContinueScanning()
    }

    LaunchedEffect(inboundMode, uiState.componentDetail, uiState.lastRawText) {
        val token = uiState.lastRawText
        val component = uiState.componentDetail
        val payload = uiState.parsedPayload
        if (token == null || component == null) {
            return@LaunchedEffect
        }
        if (inboundMode == InboundMode.Scan) {
            dialogState = InboundDialogState(
                title = context.getString(R.string.inbound_scan_dialog_title),
                component = component,
                initialQuantity = payload?.quantity ?: 1,
                quantityEditable = true,
                initialLocation = stockInLocationSuggester.suggestLocationCode(
                    component = component,
                    existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                    availableLocations = uiState.locations,
                    locationCategoryProfiles = uiState.locationCategoryProfiles,
                    fallbackCode = uiState.defaultLocationCode ?: "A1"
                ),
                availableLocations = uiState.locations,
                rawPayload = payload?.rawText,
                existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                onEdit = openManualInboundEditor,
                onConfirm = { quantity, locationCode ->
                    onConfirmInbound(
                        component,
                        quantity,
                        locationCode,
                        "QRCODE",
                        payload?.rawText
                    ) {
                        onContinueScanning()
                    }
                },
                onCancel = {
                    onContinueScanning()
                }
            )
        }
    }

    val handleModeSelected: (InboundMode) -> Unit = { mode ->
        inboundMode = mode
        if (mode != InboundMode.Scan) {
            torchEnabled = false
        }
        if (mode == InboundMode.Manual) {
            Log.d(
                INBOUND_SCREEN_TAG,
                "handleModeSelected entered manual mode, requesting next manual inbound part number refresh"
            )
            onRefreshNextManualInboundPartNumber()
            manualName = ""
            manualBrand = ""
            manualPackageName = ""
            manualCategory = ""
            manualSpecEditorVisible = false
            manualQuantityText = ""
            manualDescription = ""
            manualSpecLines = ""
            manualSourceUrl = ""
            manualSourceUrlOriginal = ""
            manualImageLocalPath = ""
            manualImageUrl = ""
            manualEntryError = null
            manualLocationCode = MANUAL_INBOUND_DEFAULT_LOCATION_CODE
        }
        dialogState = null
        onContinueScanning()
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        InboundHeader(
            inboundMode = inboundMode,
            onModeSelected = handleModeSelected,
            modifier = Modifier.fillMaxWidth()
        )

        if (inboundMode == InboundMode.Scan) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ScannerCard(
                    hasCameraPermission = hasCameraPermission,
                    scannerPaused = scannerPaused,
                    torchEnabled = torchEnabled,
                    onTorchAvailabilityChanged = { available ->
                        torchAvailable = available
                        if (!available && torchEnabled) {
                            torchEnabled = false
                        }
                    },
                    onQrScanned = onQrScanned,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxSize(),
                    fullBleed = true
                )
                FilledIconButton(
                    onClick = { torchEnabled = !torchEnabled },
                    enabled = hasCameraPermission && torchAvailable && !scannerPaused,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = stringResource(
                            if (torchEnabled) {
                                R.string.inbound_flashlight_on
                            } else {
                                R.string.inbound_flashlight_off
                            }
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (inboundMode) {
                    InboundMode.Search -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualKeyword,
                                    onValueChange = { value ->
                                        val sanitized = value.replace("\r", "").replace("\n", "")
                                        val shouldSearch = value.contains('\n') || value.contains('\r')
                                        manualKeyword = sanitized
                                        if (shouldSearch) {
                                            onManualSearch(sanitized)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(text = stringResource(R.string.inbound_manual_keyword_label)) },
                                    placeholder = { Text(text = stringResource(R.string.inbound_manual_keyword_placeholder)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { onManualSearch(manualKeyword) }
                                    )
                                )
                                Button(
                                    onClick = { onManualSearch(manualKeyword) }
                                ) {
                                    Text(text = stringResource(R.string.inbound_manual_search_action))
                                }
                            }
                        }
                        if (uiState.recentManualSearches.isNotEmpty()) {
                            item {
                                RecentManualSearchesCard(
                                    keywords = uiState.recentManualSearches,
                                    onSearchClick = { keyword ->
                                        manualKeyword = keyword
                                        onManualSearch(keyword)
                                    }
                                )
                            }
                        }
                        if (uiState.isSearchingManual) {
                            item { LoadingCard(text = stringResource(R.string.inbound_manual_searching)) }
                        }
                        uiState.manualSearchError?.let { message ->
                            item { PayloadFieldCard(title = stringResource(R.string.inbound_manual_result_title), value = message) }
                        }
                        items(uiState.manualSearchResults, key = { it.partNumber + (it.mpn ?: "") }) { component ->
                            ManualSearchResultCard(
                                component = component,
                                hasExistingStock = uiState.existingStockByPartNumber[component.partNumber].isNullOrEmpty().not(),
                                onPrintClick = {
                                    qrPreviewComponent = component
                                    qrPreviewBitmap = null
                                    qrPreviewLoading = true
                                    qrPreviewSaving = false
                                    coroutineScope.launch {
                                        MaterialQrCodeExporter.createPreviewBitmap(
                                            context = context,
                                            component = component
                                        ).onSuccess { bitmap ->
                                            qrPreviewBitmap = bitmap
                                        }.onFailure { error ->
                                            qrPreviewComponent = null
                                            Toast.makeText(
                                                context,
                                                error.message ?: context.getString(R.string.inbound_manual_print_qr_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        qrPreviewLoading = false
                                    }
                                },
                                onInboundClick = {
                                    dialogState = InboundDialogState(
                                    title = context.getString(R.string.inbound_manual_dialog_title),
                                    component = component,
                                    initialQuantity = 0,
                                    quantityEditable = true,
                                    initialLocation = stockInLocationSuggester.suggestLocationCode(
                                        component = component,
                                        existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                                        availableLocations = uiState.locations,
                                        locationCategoryProfiles = uiState.locationCategoryProfiles,
                                        fallbackCode = uiState.defaultLocationCode ?: "A1"
                                    ),
                                    availableLocations = uiState.locations,
                                    rawPayload = null,
                                    existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                                    onEdit = openManualInboundEditor,
                                    onConfirm = { quantity, locationCode ->
                                            onConfirmInbound(
                                                component,
                                                quantity,
                                                locationCode,
                                                "MANUAL",
                                                null
                                            ) { }
                                        },
                                        onCancel = {}
                                    )
                                }
                            )
                        }
                    }
                    InboundMode.Manual -> {
                        item {
                            ManualInboundEditorCard(
                                component = manualEntryComponent,
                                partNumber = manualPartNumber,
                                name = manualName,
                                onNameChange = {
                                    manualName = it
                                    manualEntryError = null
                                },
                                brand = manualBrand,
                                onBrandChange = {
                                    manualBrand = it
                                    manualEntryError = null
                                },
                                packageName = manualPackageName,
                                onPackageNameChange = {
                                    manualPackageName = it
                                    manualEntryError = null
                                },
                                category = manualCategory,
                                onCategoryChange = {
                                    manualCategory = it
                                    manualEntryError = null
                                },
                                quantityText = manualQuantityText,
                                onQuantityTextChange = {
                                    manualQuantityText = it.filter(Char::isDigit)
                                    manualEntryError = null
                                },
                                description = manualDescription,
                                onDescriptionChange = {
                                    manualDescription = it
                                    manualEntryError = null
                                },
                                sourceUrl = manualSourceUrl,
                                originalSourceUrl = manualSourceUrlOriginal,
                                onSourceUrlChange = {
                                    manualSourceUrl = it
                                    manualEntryError = null
                                },
                                onImagePreviewClick = { showManualImagePicker = true },
                                specificationText = manualSpecLines,
                                onSpecificationTextChange = {
                                    manualSpecLines = it
                                    manualEntryError = null
                                },
                                showSpecificationEditor = manualSpecEditorVisible,
                                errorMessage = manualEntryError,
                                locationLabel = resolvedManualLocationLabel,
                                onLocationClick = { showManualLocationPicker = true },
                                onInboundClick = {
                                    val confirmedPartNumber = manualEntryComponent.partNumber
                                    onConfirmInbound(
                                        manualEntryComponent,
                                        manualQuantityText.toIntOrNull() ?: 0,
                                        resolvedManualLocationCode,
                                        "MANUAL_INPUT",
                                        null
                                    ) { errorMessage ->
                                        manualEntryError = errorMessage
                                        if (errorMessage != null) {
                                            return@onConfirmInbound
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.inbound_manual_entry_success,
                                                confirmedPartNumber
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        manualName = ""
                                        manualBrand = ""
                                        manualPackageName = ""
                                        manualCategory = ""
                                        manualQuantityText = ""
                                        manualDescription = ""
                                        manualSourceUrl = ""
                                        manualSourceUrlOriginal = ""
                                        manualImageLocalPath = ""
                                        manualImageUrl = ""
                                        manualSpecLines = ""
                                        manualSpecEditorVisible = false
                                        manualEntryError = null
                                        manualLocationCode = MANUAL_INBOUND_DEFAULT_LOCATION_CODE
                                    }
                                }
                            )
                        }
                    }
                    InboundMode.Scan -> Unit
                }
            }
        }
    }

    dialogState?.let { state ->
        InboundConfirmDialog(
            state = state,
            onDismiss = {
                state.onCancel()
                dialogState = null
            },
            onConfirm = { quantity, locationCode ->
                state.onConfirm(quantity, locationCode)
                dialogState = null
            },
            onViewExistingStock = { locationCode, partNumber ->
                state.onCancel()
                dialogState = null
                onViewInventoryItem(locationCode, partNumber)
            },
            onEdit = { component ->
                state.onCancel()
                dialogState = null
                (state.onEdit ?: openManualInboundEditor)(component)
            }
        )
    }

    qrPreviewComponent?.let { component ->
        QrLabelPreviewDialog(
            component = component,
            bitmap = qrPreviewBitmap,
            printerState = printerState,
            loading = qrPreviewLoading,
            saving = qrPreviewSaving,
            printing = qrPreviewPrinting,
            onDismiss = {
                qrPreviewComponent = null
                qrPreviewBitmap = null
                qrPreviewLoading = false
            },
            onWriteNfc = {
                appContainer.nfcLabelManager.setPendingWrite(
                    NfcLabelPayloadCodec.materialUri(component.partNumber)
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.nfc_tap_tag_to_write),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPrint = { bitmap ->
                qrPreviewPrinting = true
                printerManager.printBitmap(bitmap) { errorMessage ->
                    qrPreviewPrinting = false
                    Toast.makeText(
                        context,
                        errorMessage ?: context.getString(R.string.printer_print_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onSave = { bitmap ->
                qrPreviewSaving = true
                coroutineScope.launch {
                    MaterialQrCodeExporter.saveBitmapToGallery(
                        context = context,
                        partNumber = component.partNumber,
                        bitmap = bitmap
                    ).onSuccess { fileName ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.inbound_manual_print_qr_saved, fileName),
                            Toast.LENGTH_SHORT
                        ).show()
                        qrPreviewComponent = null
                        qrPreviewBitmap = null
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(R.string.inbound_manual_print_qr_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    qrPreviewSaving = false
                }
            }
        )
    }

    if (showManualImagePicker) {
        AlertDialog(
            onDismissRequest = { showManualImagePicker = false },
            title = { Text(text = stringResource(R.string.inbound_manual_entry_pick_image_title)) },
            text = { Text(text = stringResource(R.string.inbound_manual_entry_pick_image_body)) },
            dismissButton = {
                TextButton(onClick = { showManualImagePicker = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showManualImagePicker = false
                            manualImageCameraLauncher.launch(null)
                        }
                    ) {
                        Text(text = stringResource(R.string.inbound_manual_entry_take_photo))
                    }
                    TextButton(
                        onClick = {
                            showManualImagePicker = false
                            manualImageGalleryLauncher.launch("image/*")
                        }
                    ) {
                        Text(text = stringResource(R.string.inbound_manual_entry_pick_gallery))
                    }
                }
            }
        )
    }

    if (showManualLocationPicker) {
        LocationPickerDialog(
            title = stringResource(R.string.inbound_pick_location),
            options = uiState.locations.map { location ->
                LocationPickerOption(
                    code = location.code,
                    displayName = location.displayName,
                    colorHex = location.colorHex
                )
            },
            selectedCode = resolvedManualLocationCode,
            currentOption = uiState.locations
                .firstOrNull { it.code == resolvedManualLocationCode }
                ?.let { location ->
                    LocationPickerOption(
                        code = location.code,
                        displayName = location.displayName,
                        colorHex = location.colorHex
                    )
                },
            onSelect = { code ->
                manualLocationCode = code
                showManualLocationPicker = false
            },
            onDismiss = { showManualLocationPicker = false }
        )
    }
}

@Composable
private fun InboundHeader(
    inboundMode: InboundMode,
    onModeSelected: (InboundMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleModes = listOf(InboundMode.Search, InboundMode.Scan, InboundMode.Manual)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.inbound_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        TabRow(selectedTabIndex = visibleModes.indexOf(inboundMode).coerceAtLeast(0)) {
            visibleModes.forEach { mode ->
                Tab(
                    selected = inboundMode == mode,
                    onClick = { onModeSelected(mode) },
                    text = { Text(text = stringResource(mode.titleRes)) }
                )
            }
        }
    }
}

private enum class InboundMode(val titleRes: Int) {
    Search(R.string.inbound_mode_search),
    Manual(R.string.inbound_mode_manual),
    Scan(R.string.inbound_mode_scan)
}

private const val INBOUND_SCREEN_TAG = "InboundScreen"

@Composable
private fun RecentManualSearchesCard(
    keywords: List<String>,
    onSearchClick: (String) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.inbound_recent_searches),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 2.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    OutlinedButton(
                        onClick = { onSearchClick(keyword) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(text = keyword)
                    }
                }
            }
        }
    }
}

private data class InboundDialogState(
    val title: String,
    val component: ComponentDetail,
    val initialQuantity: Int,
    val quantityEditable: Boolean,
    val initialLocation: String,
    val availableLocations: List<StorageLocation>,
    val rawPayload: String?,
    val existingStockLocations: List<ExistingStockLocation>,
    val onEdit: ((ComponentDetail) -> Unit)? = null,
    val onConfirm: (Int, String) -> Unit,
    val onCancel: () -> Unit
)

private fun buildManualInboundComponent(
    partNumber: String,
    name: String,
    brand: String,
    packageName: String,
    category: String,
    description: String,
    sourceUrl: String,
    imageLocalPath: String,
    imageUrl: String,
    specificationText: String
): ComponentDetail {
    val normalizedPartNumber = partNumber.trim().uppercase()
    return ComponentDetail(
        partNumber = normalizedPartNumber,
        mpn = null,
        name = name.trim().ifBlank { null },
        brand = brand.trim().ifBlank { null },
        packageName = packageName.trim().ifBlank { null },
        category = category.trim().ifBlank { null },
        description = description.trim().ifBlank { null },
        stockQuantity = null,
        price = null,
        productUrl = sourceUrl.trim().ifBlank { null },
        datasheetUrl = null,
        imageLocalPath = imageLocalPath.trim().ifBlank { null },
        imageUrl = imageUrl.trim().ifBlank { null },
        specifications = parseManualSpecifications(specificationText)
    )
}

private fun groupInboundLocations(locations: List<StorageLocation>): List<Pair<String, List<StorageLocation>>> {
    return locations
        .sortedBy { LegacyLocationCode(it.code) }
        .groupBy { LegacyLocationCode(it.code).rowLabel }
        .toList()
        .sortedBy { it.first }
}

@Composable
private fun parseInboundLocationColorOrDefault(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return try {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

@Composable
private fun ManualSearchResultCard(
    component: ComponentDetail,
    hasExistingStock: Boolean,
    onPrintClick: () -> Unit,
    onInboundClick: () -> Unit
) {
    val secondarySummary = remember(component) { inboundComponentSecondarySummary(component) }

    MaterialListCard(
        title = component.name ?: component.mpn ?: component.partNumber,
        subtitle = listOfNotNull(component.brand, component.packageName, component.category).joinToString(" · "),
        secondarySummary = secondarySummary,
        sourceText = component.productUrl,
        imageModel = component.imageUrl?.takeIf { it.isNotBlank() },
        imageContentDescription = component.name ?: component.partNumber,
        placeholderText = component.partNumber,
        onClick = onInboundClick,
        titleTrailing = {
            if (hasExistingStock) {
                Text(
                    text = stringResource(R.string.inbound_existing_stock_badge),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        },
        detailContent = {
            ManualSearchMetaLine(
                label = stringResource(R.string.inbound_component_number),
                value = component.partNumber
            )
        },
        bottomContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrintClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.inbound_manual_print_qr))
                }
                Button(
                    onClick = onInboundClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.inbound_manual_confirm_action))
                }
            }
        }
    )
}

@Composable
private fun ManualSearchMetaLine(
    label: String,
    value: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun inboundComponentSecondarySummary(component: ComponentDetail): String? {
    val preferredKeys = listOf("电阻类型", "阻值", "容值", "精度", "功率")
    val summary = buildList {
        preferredKeys.forEach { key ->
            component.specifications[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
        component.specifications
            .filterKeys { it !in preferredKeys }
            .toSortedMap()
            .values
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .forEach(::add)
    }.distinct().joinToString(" · ")

    return summary.takeIf { it.isNotBlank() }
}

@Composable
private fun InboundConfirmDialog(
    state: InboundDialogState,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
    onViewExistingStock: (String, String) -> Unit,
    onEdit: (ComponentDetail) -> Unit
) {
    val initialQuantityText = remember(state) {
        if (state.quantityEditable) {
            state.initialQuantity
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        } else {
            state.initialQuantity.coerceAtLeast(1).toString()
        }
    }
    var quantityText by remember(state) { mutableStateOf(initialQuantityText) }
    var locationText by remember(state) { mutableStateOf(state.initialLocation) }
    val hasExistingStock = state.existingStockLocations.isNotEmpty()
    val confirmedQuantity = if (state.quantityEditable) {
        if (quantityText.isBlank()) {
            0
        } else {
            quantityText.toIntOrNull()?.takeIf { it >= 0 }
        }
    } else {
        state.initialQuantity.coerceAtLeast(1)
    }

    MaterialInboundDialog(
        title = state.title,
        component = state.component,
        isLoading = false,
        loadingText = stringResource(R.string.inbound_component_loading),
        errorMessage = null,
        existingStockLocations = if (hasExistingStock) state.existingStockLocations else emptyList(),
        quantityText = quantityText,
        quantityEditable = state.quantityEditable,
        quantityLabel = stringResource(
            if (hasExistingStock && state.quantityEditable) {
                R.string.inbound_quantity_increment_label
            } else {
                R.string.inbound_quantity_label
            }
        ),
        onQuantityChange = { quantityText = it.filter(Char::isDigit) },
        quantityShowUndo = quantityText != initialQuantityText,
        onQuantityUndo = { quantityText = initialQuantityText },
        selectedLocationCode = locationText.ifBlank { state.initialLocation },
        availableLocations = state.availableLocations,
        onLocationSelected = { locationText = it },
        onDismiss = onDismiss,
        onEdit = { onEdit(state.component) },
        onConfirm = {
            confirmedQuantity?.let { quantity ->
                onConfirm(quantity, locationText.ifBlank { "A1" })
            }
        },
        confirmEnabled = confirmedQuantity != null,
        confirmText = stringResource(R.string.common_confirm),
        onViewExistingStock = {
            val targetLocation = state.existingStockLocations.firstOrNull() ?: return@MaterialInboundDialog
            onViewExistingStock(targetLocation.locationCode, state.component.partNumber)
        }
    )
}

@Composable
private fun InboundLocationCard(
    location: StorageLocation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(parseInboundLocationColorOrDefault(location.colorHex))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = location.displayName?.takeIf { it.isNotBlank() } ?: location.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = location.code,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ExistingStockReminderCard(
    existingStockLocations: List<ExistingStockLocation>,
    onViewItem: (() -> Unit)? = null
) {
    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3C4)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.inbound_existing_stock_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.inbound_existing_stock_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B4F00)
            )
            existingStockLocations.forEach { stock ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.inbound_existing_stock_item,
                            formatLocationLabel(stock.locationCode, stock.locationDisplayName),
                            stock.quantity
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4A3600),
                        modifier = Modifier.weight(1f)
                    )
                    if (onViewItem != null) {
                        Text(
                            text = stringResource(R.string.inbound_existing_stock_view_item),
                            modifier = Modifier
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .combinedClickable(
                                    onClick = onViewItem,
                                    onLongClick = onViewItem
                                )
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatLocationLabel(code: String, displayName: String?): String {
    val normalizedName = displayName?.trim().orEmpty()
    return if (normalizedName.isNotEmpty() && normalizedName != code) {
        "$code:$normalizedName"
    } else {
        code
    }
}

@Composable
fun ComponentDetailTable(
    component: ComponentDetail
) {
    val firstPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_number) to component.partNumber)
        add(stringResource(R.string.inbound_component_brand) to (component.brand ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_package) to (component.packageName ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_category) to (component.category ?: stringResource(R.string.inbound_field_empty)))
    }

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
        KeyValueGridCard(
            rows = firstPropertyRows
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        KeyValueTableCard(
            rows = secondPropertyRows
        )
    }
}

@Composable
private fun KeyValueGridCard(
    rows: List<Pair<String, String>>
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                KeyValueTableRow(label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun KeyValueTableCard(
    rows: List<Pair<String, String>>
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                KeyValueTableRow(label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun KeyValueTableRow(
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
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
        )
    }
}

@Composable
private fun LoadingCard(text: String = stringResource(R.string.inbound_component_loading)) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

