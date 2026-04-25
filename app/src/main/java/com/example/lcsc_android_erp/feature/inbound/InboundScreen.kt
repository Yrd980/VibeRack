package com.example.lcsc_android_erp.feature.inbound

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.printer.PrinterConnectionState
import com.example.lcsc_android_erp.core.printer.Q5PrinterManager
import com.example.lcsc_android_erp.core.ui.LocationPickerDialog
import com.example.lcsc_android_erp.core.ui.LocationPickerOption
import com.example.lcsc_android_erp.core.ui.MaterialListCard
import com.example.lcsc_android_erp.core.ui.QuantityOutlinedTextField
import com.example.lcsc_android_erp.core.ui.SourceOutlinedTextField
import com.example.lcsc_android_erp.core.ui.clearFocusOnTapOutside
import com.example.lcsc_android_erp.core.ui.performCopyFeedback
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.StorageLocation
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun InboundRoute(
    onViewInventoryItem: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as LcscApplication).appContainer
    val viewModel: InboundViewModel = viewModel(
        factory = InboundViewModel.factory(appContainer)
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    InboundScreen(
        modifier = modifier,
        uiState = uiState.value,
        q5PrinterManager = appContainer.q5PrinterManager,
        onQrScanned = viewModel::onQrScanned,
        onContinueScanning = viewModel::clearScanResult,
        onManualSearch = viewModel::searchManual,
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
    q5PrinterManager: Q5PrinterManager,
    onQrScanned: (String) -> Unit,
    onContinueScanning: () -> Unit,
    onManualSearch: (String) -> Unit,
    onRefreshExistingStock: (String) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String, String, String?, (String?) -> Unit) -> Unit,
    onViewInventoryItem: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerState by q5PrinterManager.state.collectAsStateWithLifecycle()
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
    val resolvedManualLocationCode = manualLocationCode.ifBlank {
        uiState.defaultLocationCode
            ?: uiState.locations.firstOrNull()?.code
            ?: "A1"
    }
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
        manualSourceUrl = component.productUrl.orEmpty()
        manualSourceUrlOriginal = component.productUrl.orEmpty()
        manualImageLocalPath = component.imageLocalPath.orEmpty()
        manualImageUrl = component.imageUrl.orEmpty()
        manualSpecLines = component.specifications.entries.joinToString("\n") { (key, value) ->
            "$key=$value"
        }
        manualLocationCode = suggestInboundLocationCode(
            category = component.category,
            existingStockLocations = emptyList(),
            availableLocations = uiState.locations,
            fallbackCode = uiState.defaultLocationCode ?: "A1"
        )
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
                initialLocation = suggestInboundLocationCode(
                    category = component.category,
                    existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                    availableLocations = uiState.locations,
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
            manualLocationCode = uiState.defaultLocationCode
                ?: uiState.locations.firstOrNull()?.code
                ?: "A1"
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
                                    initialLocation = suggestInboundLocationCode(
                                        category = component.category,
                                        existingStockLocations = uiState.existingStockByPartNumber[component.partNumber].orEmpty(),
                                        availableLocations = uiState.locations,
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
                                        manualLocationCode = uiState.defaultLocationCode
                                            ?: uiState.locations.firstOrNull()?.code
                                            ?: "A1"
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
        AlertDialog(
            onDismissRequest = {
                if (!qrPreviewSaving && !qrPreviewPrinting) {
                    qrPreviewComponent = null
                    qrPreviewBitmap = null
                    qrPreviewLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            title = { Text(text = stringResource(R.string.inbound_manual_print_qr_preview_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (qrPreviewLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(text = stringResource(R.string.inbound_manual_print_qr_preview_loading))
                        }
                    } else {
                        qrPreviewBitmap?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = component.name ?: component.partNumber,
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
                        if (qrPreviewPrinting || printerState.isPrinting) {
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
                        qrPreviewComponent = null
                        qrPreviewBitmap = null
                        qrPreviewLoading = false
                    },
                    enabled = !qrPreviewSaving && !qrPreviewPrinting
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val bitmap = qrPreviewBitmap ?: return@TextButton
                            qrPreviewPrinting = true
                            q5PrinterManager.printBitmap(bitmap) { errorMessage ->
                                qrPreviewPrinting = false
                                Toast.makeText(
                                    context,
                                    errorMessage ?: context.getString(R.string.printer_print_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !qrPreviewLoading &&
                            !qrPreviewSaving &&
                            !qrPreviewPrinting &&
                            qrPreviewBitmap != null &&
                            printerState.connectionState == PrinterConnectionState.CONNECTED &&
                            !printerState.isPrinting
                    ) {
                        Text(text = stringResource(R.string.printer_print_label))
                    }
                    TextButton(
                        onClick = {
                            val bitmap = qrPreviewBitmap ?: return@TextButton
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
                        },
                        enabled = !qrPreviewLoading && !qrPreviewSaving && !qrPreviewPrinting && qrPreviewBitmap != null
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
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

@Composable
private fun ManualInboundEditorCard(
    component: ComponentDetail,
    partNumber: String,
    name: String,
    onNameChange: (String) -> Unit,
    brand: String,
    onBrandChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    sourceUrl: String,
    originalSourceUrl: String,
    onSourceUrlChange: (String) -> Unit,
    onImagePreviewClick: () -> Unit,
    specificationText: String,
    onSpecificationTextChange: (String) -> Unit,
    showSpecificationEditor: Boolean,
    errorMessage: String?,
    locationLabel: String,
    onLocationClick: () -> Unit,
    onInboundClick: () -> Unit
) {
    val context = LocalContext.current
    val openableSourceUrl = remember(sourceUrl) { extractOpenableSourceUrl(sourceUrl) }
    val sourceChanged = sourceUrl != originalSourceUrl
    Column(
        modifier = Modifier.clearFocusOnTapOutside(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Card(
                modifier = Modifier
                    .width(168.dp)
                    .combinedClickable(
                        onClick = onImagePreviewClick,
                        onLongClick = onImagePreviewClick
                    )
            ) {
                val imageModel = component.imageLocalPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.exists() && it.length() > 0L }
                    ?: component.imageUrl?.takeIf { it.isNotBlank() }
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = component.name ?: component.partNumber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = component.partNumber.ifBlank { stringResource(R.string.inbound_component_number) },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.inbound_manual_entry_pick_image_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            ManualInboundEditableFirstPropertyCard(
                partNumber = partNumber,
                brand = brand,
                onBrandChange = onBrandChange,
                packageName = packageName,
                onPackageNameChange = onPackageNameChange,
                category = category,
                onCategoryChange = onCategoryChange,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        ManualInboundEditableSecondPropertyCard(
            name = name,
            onNameChange = onNameChange,
            specificationText = specificationText,
            onSpecificationTextChange = onSpecificationTextChange,
            showSpecificationEditor = showSpecificationEditor,
            description = description,
            onDescriptionChange = onDescriptionChange
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val originalQuantityText = ""
            QuantityOutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.inbound_quantity_label),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                onDecrease = {
                    val current = quantityText.toIntOrNull() ?: 0
                    onQuantityTextChange((current - 1).coerceAtLeast(0).toString())
                },
                decreaseContentDescription = stringResource(R.string.common_decrease),
                onIncrease = {
                    val current = quantityText.toIntOrNull()
                    onQuantityTextChange(((current ?: 0) + 1).toString())
                },
                increaseContentDescription = stringResource(R.string.common_increase),
                showUndo = quantityText != originalQuantityText,
                onUndo = { onQuantityTextChange(originalQuantityText) },
                undoContentDescription = stringResource(R.string.common_undo)
            )
            SourceOutlinedTextField(
                value = sourceUrl,
                onValueChange = onSourceUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.inventory_source_label),
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Default
                ),
                showUndo = sourceChanged,
                onUndo = { onSourceUrlChange(originalSourceUrl) },
                undoContentDescription = stringResource(R.string.common_undo),
                onValueBlurTransform = ::normalizeSourceValue,
                showOpen = openableSourceUrl != null,
                onOpen = {
                    openableSourceUrl ?: return@SourceOutlinedTextField
                    runCatching {
                        openSourceUrl(context, openableSourceUrl)
                    }.onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.inventory_open_source_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                openContentDescription = stringResource(R.string.inventory_open_source)
            )
        }
        errorMessage?.let { message ->
            PayloadFieldCard(
                title = stringResource(R.string.inbound_component_error_title),
                value = message
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onLocationClick) {
                Text(text = locationLabel)
            }
            Button(onClick = onInboundClick) {
                Text(text = stringResource(R.string.common_confirm))
            }
        }
    }
}

private fun normalizeSourceValue(value: String): String {
    val normalized = value.trim()
    return extractOpenableSourceUrl(normalized) ?: normalized
}

@Composable
private fun ManualInboundEditableFirstPropertyCard(
    partNumber: String,
    brand: String,
    onBrandChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_number),
                value = partNumber,
                onValueChange = {},
                readOnly = true
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_brand),
                value = brand,
                onValueChange = onBrandChange
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_package),
                value = packageName,
                onValueChange = onPackageNameChange
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_category),
                value = category,
                onValueChange = onCategoryChange
            )
        }
    }
}

@Composable
private fun ManualInboundEditableSecondPropertyCard(
    name: String,
    onNameChange: (String) -> Unit,
    specificationText: String,
    onSpecificationTextChange: (String) -> Unit,
    showSpecificationEditor: Boolean,
    description: String,
    onDescriptionChange: (String) -> Unit,
) {
    val specificationEntries = remember(specificationText) {
        parseManualSpecificationEntries(specificationText)
    }
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            ManualInboundEditableRow(
                label = stringResource(R.string.inbound_component_name),
                value = name,
                onValueChange = onNameChange
            )
            if (showSpecificationEditor) {
                specificationEntries.forEachIndexed { index, (key, value) ->
                    HorizontalDivider()
                    ManualInboundEditableRow(
                        label = key,
                        value = value,
                        onValueChange = { newValue ->
                            onSpecificationTextChange(
                                rebuildManualSpecificationText(
                                    specificationEntries.mapIndexed { entryIndex, entry ->
                                        if (entryIndex == index) {
                                            entry.first to newValue
                                        } else {
                                            entry
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
            }
            HorizontalDivider()
            ManualInboundEditableRow(
                label = stringResource(R.string.inbound_component_description),
                value = description,
                onValueChange = onDescriptionChange,
                singleLine = false,
                minLines = 3
            )
        }
    }
}

@Composable
private fun ManualInboundEditableCell(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ManualInboundEditableText(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly
        )
    }
}

@Composable
private fun ManualInboundEditableRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        ManualInboundEditableText(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            minLines = minLines,
            placeholder = placeholder
        )
    }
}

@Composable
private fun ManualInboundEditableText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null
) {
    Box(modifier = modifier) {
        if (value.isBlank() && !placeholder.isNullOrBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            )
        )
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

private fun parseManualSpecifications(rawText: String): Map<String, String> {
    return parseManualSpecificationEntries(rawText).toMap()
}

private fun parseManualSpecificationEntries(rawText: String): List<Pair<String, String>> {
    return rawText
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separatorIndex = line.indexOfFirst { it == ':' || it == '=' || it == '：' }
            if (separatorIndex <= 0 || separatorIndex >= line.lastIndex) {
                return@mapNotNull null
            }
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            if (key.isEmpty() || value.isEmpty()) {
                null
            } else {
                key to value
            }
        }
        .toList()
}

private fun rebuildManualSpecificationText(entries: List<Pair<String, String>>): String {
    return entries.joinToString("\n") { (key, value) -> "$key=$value" }
}

private fun saveManualInboundBitmap(
    context: android.content.Context,
    partNumber: String,
    bitmap: Bitmap
): String {
    val imageDir = File(context.filesDir, "manual_inbound_images").apply { mkdirs() }
    val targetFile = File(imageDir, "${partNumber.trim().uppercase().ifBlank { "MANUAL" }}.jpg")
    saveBitmapToFile(
        bitmap = resizeBitmapWithinLimit(bitmap),
        targetFile = targetFile,
        format = Bitmap.CompressFormat.JPEG
    )
    return targetFile.absolutePath
}

private fun saveManualInboundImageUri(
    context: android.content.Context,
    partNumber: String,
    uri: Uri
): String {
    val imageDir = File(context.filesDir, "manual_inbound_images").apply { mkdirs() }
    val (extension, format) = when (context.contentResolver.getType(uri)?.lowercase()) {
        "image/png" -> "png" to Bitmap.CompressFormat.PNG
        "image/webp" -> "webp" to Bitmap.CompressFormat.WEBP_LOSSY
        else -> "jpg" to Bitmap.CompressFormat.JPEG
    }
    val targetFile = File(imageDir, "${partNumber.trim().uppercase().ifBlank { "MANUAL" }}.$extension")
    val bitmap = decodeBitmapWithinLimit(context, uri) ?: error("Failed to decode selected image")
    saveBitmapToFile(
        bitmap = bitmap,
        targetFile = targetFile,
        format = format
    )
    return targetFile.absolutePath
}

private fun decodeBitmapWithinLimit(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int = 300
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    resolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, bounds)
    } ?: return null

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = maxDimension
        )
    }
    val decodedBitmap = resolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, sampledOptions)
    } ?: return null

    return resizeBitmapWithinLimit(decodedBitmap, maxDimension)
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int
): Int {
    if (width <= 0 || height <= 0 || (width <= maxDimension && height <= maxDimension)) {
        return 1
    }
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth > maxDimension || sampledHeight > maxDimension) {
        sampleSize *= 2
        sampledWidth = width / sampleSize
        sampledHeight = height / sampleSize
    }
    return sampleSize.coerceAtLeast(1)
}

private fun resizeBitmapWithinLimit(
    bitmap: Bitmap,
    maxDimension: Int = 300
): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }
    val scale = minOf(
        maxDimension.toFloat() / width.toFloat(),
        maxDimension.toFloat() / height.toFloat()
    )
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun saveBitmapToFile(
    bitmap: Bitmap,
    targetFile: File,
    format: Bitmap.CompressFormat
) {
    FileOutputStream(targetFile).use { outputStream ->
        val quality = when (format) {
            Bitmap.CompressFormat.PNG -> 100
            else -> 92
        }
        if (!bitmap.compress(format, quality, outputStream)) {
            error("Failed to compress bitmap")
        }
        outputStream.flush()
    }
}

private fun extractOpenableSourceUrl(value: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty()) {
        return null
    }
    val urlRegex = Regex("""(?i)\bhttps?://[^\s"”」】]+""")
    return urlRegex.find(normalized)?.value?.trim()
}

private fun openSourceUrl(context: android.content.Context, rawUrl: String) {
    val normalizedUrl = rawUrl.trim()
    val browserUri = normalizedUrl.toUri()
    val intent = Intent(Intent.ACTION_VIEW, browserUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching {
        context.startActivity(intent)
    }.isSuccess
    if (!launched) {
        throw IllegalStateException("No activity can handle source url: $normalizedUrl")
    }
}

@Composable
fun ScannerCard(
    hasCameraPermission: Boolean,
    scannerPaused: Boolean,
    torchEnabled: Boolean = false,
    onTorchAvailabilityChanged: (Boolean) -> Unit = {},
    onQrScanned: (String) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    fullBleed: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fullBleed) {
                    Modifier
                } else {
                    Modifier.clip(MaterialTheme.shapes.large)
                }
            )
    ) {
        if (hasCameraPermission) {
            QrScannerPreview(
                modifier = Modifier.fillMaxSize(),
                enabled = !scannerPaused,
                torchEnabled = torchEnabled,
                onTorchAvailabilityChanged = onTorchAvailabilityChanged,
                onQrCodeDetected = onQrScanned
            )
            CameraGuideOverlay()
        } else {
            PermissionPlaceholder(onRequestPermission = onRequestPermission)
        }
    }
}

@Composable
fun CameraGuideOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(180.dp)
        ) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = size.minDimension * 0.22f
            val maxX = size.width
            val maxY = size.height

            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(cornerLength, 0f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(0f, cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(maxX - cornerLength, 0f),
                end = Offset(maxX, 0f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(maxX, 0f),
                end = Offset(maxX, cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(0f, maxY - cornerLength),
                end = Offset(0f, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, maxY),
                end = Offset(cornerLength, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(maxX - cornerLength, maxY),
                end = Offset(maxX, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(maxX, maxY - cornerLength),
                end = Offset(maxX, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun groupInboundLocations(locations: List<StorageLocation>): List<Pair<String, List<StorageLocation>>> {
    return locations
        .sortedWith(compareBy({ inboundLocationRowIndex(it.code) }, { inboundLocationColumnIndex(it.code) }, { it.code }))
        .groupBy { inboundLocationRowLabel(it.code) }
        .toList()
        .sortedBy { it.first }
}

private fun inboundLocationRowLabel(code: String): String {
    return code.takeWhile { it.isLetter() }.ifBlank { "#" }
}

private fun inboundLocationRowIndex(code: String): Int {
    return code.firstOrNull()?.uppercaseChar()?.code ?: Int.MAX_VALUE
}

private fun inboundLocationColumnIndex(code: String): Int {
    return code.dropWhile { it.isLetter() }.toIntOrNull() ?: Int.MAX_VALUE
}

private data class InboundCategoryLocationMapping(
    val keywords: List<String>,
    val prefix: String
)

private val inboundCategoryLocationMappings = listOf(
    InboundCategoryLocationMapping(listOf("电阻"), "R"),
    InboundCategoryLocationMapping(listOf("电容"), "C"),
    InboundCategoryLocationMapping(listOf("二极管", "LED", "TVS"), "D"),
    InboundCategoryLocationMapping(listOf("电感"), "L"),
    InboundCategoryLocationMapping(listOf("三极管", "晶体管", "MOS"), "Q"),
    InboundCategoryLocationMapping(listOf("晶振", "振荡器"), "Y"),
    InboundCategoryLocationMapping(listOf("保险丝"), "F"),
    InboundCategoryLocationMapping(listOf("连接器", "接插件"), "J"),
    InboundCategoryLocationMapping(listOf("继电器"), "K"),
    InboundCategoryLocationMapping(listOf("开关", "按键"), "S"),
    InboundCategoryLocationMapping(listOf("传感器"), "T"),
    InboundCategoryLocationMapping(listOf("集成电路", "接口芯片", "逻辑芯片", "放大器", "驱动器", "存储器", "处理器", "单片机"), "U")
)

private fun suggestInboundLocationCode(
    category: String?,
    existingStockLocations: List<ExistingStockLocation>,
    availableLocations: List<StorageLocation>,
    fallbackCode: String
): String {
    existingStockLocations.firstOrNull { existing ->
        availableLocations.any { location ->
            location.code.equals(existing.locationCode, ignoreCase = true)
        }
    }?.let { return it.locationCode }

    val normalizedCategory = category?.trim().orEmpty()
    if (normalizedCategory.isEmpty()) {
        return fallbackCode
    }

    val mapping = inboundCategoryLocationMappings.firstOrNull { rule ->
        rule.keywords.any { keyword -> normalizedCategory.contains(keyword, ignoreCase = true) }
    } ?: return fallbackCode

    val sortedLocations = availableLocations.sortedWith(
        compareBy<StorageLocation>(
            { inboundLocationRowIndex(it.code) },
            { inboundLocationColumnIndex(it.code) },
            { it.code }
        )
    )

    sortedLocations.firstOrNull { location ->
        location.code.trim().uppercase().startsWith(mapping.prefix)
    }?.let { return it.code }

    sortedLocations.firstOrNull { location ->
        location.displayName
            ?.trim()
            ?.uppercase()
            ?.startsWith(mapping.prefix) == true
    }?.let { return it.code }

    return mapping.prefix + "1"
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

@Composable
private fun PermissionPlaceholder(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.inbound_camera_permission_needed),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = stringResource(R.string.inbound_grant_permission))
        }
    }
}

@Composable
private fun ScanStatusCard(
    uiState: InboundUiState
) {
    val statusText = when {
        uiState.isLoadingComponent -> stringResource(R.string.inbound_status_loading_component)
        uiState.componentDetail != null -> stringResource(R.string.inbound_status_success)
        uiState.componentLookupError != null -> uiState.componentLookupError
        uiState.parseError != null -> uiState.parseError
        else -> stringResource(R.string.inbound_status_waiting)
    }

    PayloadFieldCard(
        title = stringResource(R.string.inbound_status_title),
        value = buildString {
            append(statusText)
            if (!uiState.lastRawText.isNullOrBlank()) {
                append("\n")
                append(uiState.lastRawText)
            }
        }
    )
}

@Composable
private fun PayloadFieldCard(
    title: String,
    value: String
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
