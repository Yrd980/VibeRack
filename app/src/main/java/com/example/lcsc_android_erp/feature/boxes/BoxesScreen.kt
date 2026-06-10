package com.example.lcsc_android_erp.feature.boxes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.datastore.UserPreferences
import com.example.lcsc_android_erp.core.printer.PrinterConnectionState
import com.example.lcsc_android_erp.core.printer.PrinterManager
import com.example.lcsc_android_erp.core.printer.PrinterState
import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.feature.printer.BoxLayerLabelBitmap

@Composable
fun BoxesRoute(
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as LcscApplication).appContainer
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

    BoxesScreen(
        uiState = uiState.value,
        printerManager = printerManager,
        onSelectBox = viewModel::selectBox,
        onCreateBox = viewModel::createBox,
        onClearCreateError = viewModel::clearCreateError,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerState by printerManager.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var layerCount by rememberSaveable { mutableStateOf("10") }
    var printMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.createError) {
        if (uiState.createError == null) {
            return@LaunchedEffect
        }
        printMessage = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                key = { box -> box.id }
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
                    key = { layer -> layer.id }
                ) { layer ->
                    LayerCard(
                        layer = layer,
                        printerState = printerState,
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
                        }
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
    onPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(
                    onClick = onPrint,
                    enabled = printerState.connectionState == PrinterConnectionState.CONNECTED &&
                        !printerState.isPrinting
                ) {
                    Text(text = stringResource(R.string.boxes_print_layer_label))
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
