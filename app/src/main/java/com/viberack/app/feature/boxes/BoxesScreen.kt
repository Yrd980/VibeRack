package com.viberack.app.feature.boxes

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.datastore.UserPreferences
import com.viberack.app.core.nfc.NfcLabelPayloadCodec
import com.viberack.app.core.printer.PrinterConnectionState
import com.viberack.app.core.printer.PrinterManager
import com.viberack.app.core.printer.PrinterState
import com.viberack.app.domain.model.ComponentBox
import com.viberack.app.domain.model.ComponentBoxLayer
import com.viberack.app.feature.printer.BoxLayerLabelBitmap

@Composable
fun BoxesRoute(
    openRequest: BoxesOpenRequest? = null,
    openRequestSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
    val viewModel: BoxesViewModel = viewModel(
        factory = BoxesViewModel.factory(appContainer)
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by appContainer.userPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val printerManager = remember(appContainer, preferences.printerType) {
        appContainer.printerManagerForType(preferences.printerType)
    }

    LaunchedEffect(openRequestSignal) {
        openRequest?.let { request ->
            viewModel.openLayer(request.boxCode, request.layerCode)
        }
    }

    BoxesScreen(
        uiState = uiState.value,
        printerManager = printerManager,
        onSelectBox = viewModel::selectBox,
        onCreateBox = viewModel::createBox,
        onClearCreateError = viewModel::clearCreateError,
        onBindLayer = viewModel::bindLayerComponent,
        onWriteLayerNfc = { layer ->
            layer.partNumber
                ?.takeIf { it.isNotBlank() }
                ?.let { partNumber ->
                    appContainer.nfcLabelManager.setPendingWrite(
                        NfcLabelPayloadCodec.materialUri(
                            partNumber = partNumber,
                            boxCode = layer.boxCode,
                            layerCode = layer.layerCode
                        )
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.nfc_tap_tag_to_write),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        },
        modifier = modifier
    )
}

@Composable
fun BoxesScreen(
    uiState: BoxesUiState,
    printerManager: PrinterManager,
    onSelectBox: (ComponentBox) -> Unit,
    onCreateBox: (String, String, Int) -> Unit,
    onClearCreateError: () -> Unit,
    onBindLayer: (ComponentBoxLayer, String, Int, (String?) -> Unit) -> Unit,
    onWriteLayerNfc: (ComponentBoxLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerState by printerManager.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var layerCount by rememberSaveable { mutableStateOf("10") }
    var printMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var bindTargetLayer by remember { mutableStateOf<ComponentBoxLayer?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.createError) {
        if (uiState.createError == null) {
            return@LaunchedEffect
        }
        printMessage = null
    }

    LaunchedEffect(
        uiState.selectedBox?.id,
        uiState.selectedBoxLayers.size,
        uiState.highlightedLayerId
    ) {
        if (uiState.selectedBox == null || uiState.selectedBoxLayers.isEmpty()) {
            return@LaunchedEffect
        }

        val layersTitleIndex = 2 + uiState.boxes.size
        val targetLayerIndex = uiState.highlightedLayerId?.let { highlightedId ->
            uiState.selectedBoxLayers.indexOfFirst { layer -> layer.id == highlightedId }
                .takeIf { index -> index >= 0 }
        }
        val targetItemIndex = targetLayerIndex?.let { layerIndex ->
            layersTitleIndex + 1 + layerIndex
        } ?: layersTitleIndex
        listState.animateScrollToItem(targetItemIndex)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.boxes_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            CreateBoxCard(
                code = code,
                onCodeChange = {
                    code = it
                    onClearCreateError()
                },
                name = name,
                onNameChange = {
                    name = it
                    onClearCreateError()
                },
                layerCount = layerCount,
                onLayerCountChange = {
                    layerCount = it
                    onClearCreateError()
                },
                error = uiState.createError,
                onCreate = {
                    val parsedLayerCount = layerCount.toIntOrNull() ?: 0
                    onCreateBox(code, name, parsedLayerCount)
                }
            )
        }
        if (uiState.boxes.isEmpty()) {
            item {
                StatusCard(body = stringResource(R.string.boxes_empty))
            }
        } else {
            items(
                items = uiState.boxes,
                key = { box -> "box-${box.id}" }
            ) { box ->
                BoxCard(
                    box = box,
                    selected = uiState.selectedBox?.id == box.id,
                    onClick = { onSelectBox(box) }
                )
            }
        }
        uiState.selectedBox?.let { selectedBox ->
            item {
                Text(
                    text = stringResource(R.string.boxes_layers_title, selectedBox.code),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (uiState.selectedBoxLayers.isEmpty()) {
                item {
                    StatusCard(body = stringResource(R.string.boxes_layers_empty))
                }
            } else {
                items(
                    items = uiState.selectedBoxLayers,
                    key = { layer -> "layer-${layer.id}" }
                ) { layer ->
                    LayerCard(
                        layer = layer,
                        printerState = printerState,
                        highlighted = uiState.highlightedLayerId == layer.id,
                        onBind = { bindTargetLayer = layer },
                        onPrint = {
                            if (
                                printerState.connectionState != PrinterConnectionState.CONNECTED ||
                                printerState.isPrinting
                            ) {
                                printMessage = context.getString(R.string.printer_not_connected)
                                return@LayerCard
                            }

                            val bitmap = BoxLayerLabelBitmap.create10MmBitmap(
                                positionCode = layer.positionCode,
                                partNumber = layer.partNumber.orEmpty()
                            )
                            printMessage = context.getString(R.string.printer_print_in_progress)
                            printerManager.printBitmap(bitmap) { error ->
                                printMessage = error ?: context.getString(R.string.printer_print_success)
                            }
                        },
                        onWriteNfc = { onWriteLayerNfc(layer) }
                    )
                }
            }
        }
        printMessage?.let { message ->
            item {
                StatusCard(body = message)
            }
        }
    }

    bindTargetLayer?.let { layer ->
        BindLayerDialog(
            layer = layer,
            onBind = onBindLayer,
            onDismiss = { bindTargetLayer = null }
        )
    }
}

@Composable
private fun CreateBoxCard(
    code: String,
    onCodeChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    layerCount: String,
    onLayerCountChange: (String) -> Unit,
    error: String?,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.boxes_create_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.boxes_create_code_label)) },
                placeholder = { Text(text = stringResource(R.string.boxes_create_code_placeholder)) },
                singleLine = true
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.boxes_create_name_label)) },
                placeholder = { Text(text = stringResource(R.string.boxes_create_name_placeholder)) },
                singleLine = true
            )
            OutlinedTextField(
                value = layerCount,
                onValueChange = { value ->
                    onLayerCountChange(value.filter(Char::isDigit).take(2))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.boxes_create_layer_count_label)) },
                placeholder = { Text(text = stringResource(R.string.boxes_create_layer_count_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onCreate,
                enabled = code.isNotBlank() && layerCount.isNotBlank()
            ) {
                Text(text = stringResource(R.string.boxes_create_button))
            }
        }
    }
}

@Composable
private fun BoxCard(
    box: ComponentBox,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = box.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            box.name?.takeIf { it.isNotBlank() }?.let { displayName ->
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(
                    R.string.boxes_layer_summary,
                    box.occupiedLayerCount,
                    box.layerCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LayerCard(
    layer: ComponentBoxLayer,
    printerState: PrinterState,
    highlighted: Boolean,
    onBind: () -> Unit,
    onPrint: () -> Unit,
    onWriteNfc: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = layer.positionCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = layer.partSummary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBind,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (layer.isOccupied) {
                                R.string.boxes_replace_layer_material
                            } else {
                                R.string.boxes_bind_layer_material
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    enabled = printerState.connectionState == PrinterConnectionState.CONNECTED &&
                        !printerState.isPrinting
                ) {
                    Text(
                        text = stringResource(R.string.boxes_print_layer_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (layer.isOccupied) {
                    OutlinedButton(
                        onClick = onWriteNfc,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nfc_write_tag),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (printerState.connectionState != PrinterConnectionState.CONNECTED) {
                Text(
                    text = stringResource(R.string.printer_not_connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (printerState.isPrinting) {
                Text(
                    text = stringResource(R.string.printer_print_in_progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BindLayerDialog(
    layer: ComponentBoxLayer,
    onBind: (ComponentBoxLayer, String, Int, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var partNumber by remember(layer.id) { mutableStateOf(layer.partNumber.orEmpty()) }
    var quantityText by remember(layer.id) { mutableStateOf((layer.quantity ?: 0).toString()) }
    var actionError by remember(layer.id) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(layer.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        title = { Text(text = stringResource(R.string.boxes_bind_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = layer.positionCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = partNumber,
                    onValueChange = {
                        partNumber = it
                        actionError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.boxes_bind_part_label)) },
                    placeholder = { Text(text = stringResource(R.string.printer_box_label_part_placeholder)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it.filter(Char::isDigit)
                        actionError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.boxes_bind_quantity_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                actionError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val quantity = quantityText.toIntOrNull()
                    if (quantity == null) {
                        actionError = context.getString(R.string.boxes_bind_error_invalid_input)
                        return@Button
                    }
                    isSubmitting = true
                    actionError = null
                    onBind(layer, partNumber, quantity) { error ->
                        isSubmitting = false
                        actionError = error
                        if (error == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.boxes_bind_success, layer.positionCode),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(
                    text = stringResource(
                        if (isSubmitting) {
                            R.string.boxes_bind_submitting
                        } else {
                            R.string.boxes_bind_confirm
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun StatusCard(
    body: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = body,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ComponentBoxLayer.partSummary(): String {
    val part = partNumber?.takeIf { it.isNotBlank() }
        ?: return stringResource(R.string.boxes_layer_empty)
    val quantityText = quantity?.let { " x$it" }.orEmpty()
    val nameText = componentName?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
    return stringResource(R.string.boxes_layer_occupied, part + quantityText + nameText)
}
