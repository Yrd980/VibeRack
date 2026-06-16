package com.viberack.app.feature.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.datastore.UserPreferences
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.core.printer.BondedPrinter
import com.viberack.app.core.printer.PrinterManager
import com.viberack.app.core.printer.PrinterConnectionState
import com.viberack.app.core.printer.PrinterNameMatcher
import com.viberack.app.core.printer.PrinterState
import kotlinx.coroutines.launch

@Composable
fun PrinterRoute(
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as VibeRackApplication).appContainer
    val preferences by appContainer.userPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember(appContainer, preferences.printerType) {
        appContainer.printerManagerForType(preferences.printerType)
    }
    PrinterScreen(
        printerManager = printerManager,
        printerType = preferences.printerType,
        onPrinterTypeChange = { printerType ->
            coroutineScope.launch {
                appContainer.userPreferencesRepository.setPrinterType(printerType)
            }
        },
        modifier = modifier
    )
}

@Composable
fun PrinterScreen(
    printerManager: PrinterManager,
    printerType: String,
    onPrinterTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by printerManager.state.collectAsStateWithLifecycle()
    var printMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var boxLabelPosition by rememberSaveable { mutableStateOf("BOX01-L03") }
    var boxLabelPartNumber by rememberSaveable { mutableStateOf("C17710") }
    var hasBluetoothPermission by rememberSaveable {
        mutableStateOf(hasBluetoothPermission(context, printerType))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBluetoothPermission = hasBluetoothPermission(context, printerType)
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }

    LaunchedEffect(printerManager, printerType) {
        val currentPermission = hasBluetoothPermission(context, printerType)
        hasBluetoothPermission = currentPermission
        printerManager.refreshBondedPrinters(currentPermission)
    }

    LaunchedEffect(hasBluetoothPermission) {
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }

    DisposableEffect(lifecycleOwner, printerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBluetoothPermission = hasBluetoothPermission(context, printerType)
                printerManager.refreshBondedPrinters(hasBluetoothPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val visiblePrinters = remember(state.bondedPrinters, printerType) {
        when (printerType) {
            UserPreferencesRepository.PRINTER_TYPE_DELI_Q5 -> state.bondedPrinters.filter { printer ->
                PrinterNameMatcher.isDeliQ5(printer.name)
            }

            UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0 -> state.bondedPrinters.filter { printer ->
                PrinterNameMatcher.isDetongerP0(printer.name)
            }

            else -> state.bondedPrinters
        }
    }
    val boxLabelPositionValue = boxLabelPosition.trim()
    val boxLabelPartNumberValue = boxLabelPartNumber.trim()
    val boxLabelBitmap = remember(boxLabelPositionValue, boxLabelPartNumberValue) {
        if (boxLabelPositionValue.isNotBlank() && boxLabelPartNumberValue.isNotBlank()) {
            BoxLayerLabelBitmap.create10MmBitmap(
                positionCode = boxLabelPositionValue,
                partNumber = boxLabelPartNumberValue
            )
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.printer_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            StatusCard(
                title = stringResource(R.string.printer_connection_title),
                body = when {
                    !state.bluetoothAvailable -> stringResource(R.string.printer_bluetooth_not_supported)
                    !hasBluetoothPermission -> stringResource(R.string.printer_permission_required)
                    !state.bluetoothEnabled -> stringResource(R.string.printer_bluetooth_disabled)
                    else -> state.connectionSummary
                }
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.printer_type_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrinterTypeOptionButton(
                        text = stringResource(R.string.printer_type_auto),
                        selected = printerType == UserPreferencesRepository.PRINTER_TYPE_AUTO,
                        onClick = {
                            onPrinterTypeChange(UserPreferencesRepository.PRINTER_TYPE_AUTO)
                        }
                    )
                    PrinterTypeOptionButton(
                        text = stringResource(R.string.printer_type_deli_q5),
                        selected = printerType == UserPreferencesRepository.PRINTER_TYPE_DELI_Q5,
                        onClick = {
                            onPrinterTypeChange(UserPreferencesRepository.PRINTER_TYPE_DELI_Q5)
                        }
                    )
                    PrinterTypeOptionButton(
                        text = stringResource(R.string.printer_type_yinlifang_p0),
                        selected = printerType == UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0,
                        onClick = {
                            onPrinterTypeChange(UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0)
                        }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasBluetoothPermission && bluetoothPermissions(printerType).isNotEmpty()) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                bluetoothPermissions(printerType)
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.printer_request_permission))
                    }
                }
                if (state.bluetoothAvailable && !state.bluetoothEnabled) {
                    Button(
                        onClick = {
                            enableBluetoothLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.printer_enable_bluetooth))
                    }
                }
                OutlinedButton(
                    onClick = { printerManager.refreshBondedPrinters(hasBluetoothPermission) }
                ) {
                    Text(text = stringResource(R.string.printer_refresh))
                }
                if (state.connectionState == PrinterConnectionState.CONNECTED) {
                    Button(
                        onClick = {
                            printMessage = context.getString(R.string.printer_print_in_progress)
                            printerManager.printBitmap(PrinterSmokeTestLabel.createBitmap()) { errorMessage ->
                                printMessage = errorMessage
                                    ?: context.getString(R.string.printer_print_success)
                            }
                        },
                        enabled = !state.isPrinting
                    ) {
                        Text(text = stringResource(R.string.printer_print_smoke_test))
                    }
                    OutlinedButton(
                        onClick = printerManager::disconnect
                    ) {
                        Text(text = stringResource(R.string.printer_disconnect))
                    }
                }
            }
        }
        item {
            BoxLayerLabelCard(
                positionCode = boxLabelPosition,
                onPositionCodeChange = { boxLabelPosition = it },
                partNumber = boxLabelPartNumber,
                onPartNumberChange = { boxLabelPartNumber = it },
                previewBitmap = boxLabelBitmap,
                printerState = state,
                onPrint = {
                    val bitmap = boxLabelBitmap
                    when {
                        boxLabelPositionValue.isBlank() -> {
                            printMessage = context.getString(R.string.printer_box_label_position_required)
                        }

                        boxLabelPartNumberValue.isBlank() -> {
                            printMessage = context.getString(R.string.printer_box_label_part_required)
                        }

                        bitmap == null -> {
                            printMessage = context.getString(R.string.printer_box_label_preview_unavailable)
                        }

                        else -> {
                            printMessage = context.getString(R.string.printer_print_in_progress)
                            printerManager.printBitmap(bitmap) { errorMessage ->
                                printMessage = errorMessage
                                    ?: context.getString(R.string.printer_print_success)
                            }
                        }
                    }
                }
            )
        }
        printMessage?.let { message ->
            item {
                StatusCard(body = message)
            }
        }
        item {
            Text(
                text = stringResource(R.string.printer_bonded_devices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (visiblePrinters.isEmpty()) {
            item {
                StatusCard(
                    body = if (printerType != UserPreferencesRepository.PRINTER_TYPE_AUTO) {
                        stringResource(R.string.printer_no_matching_devices)
                    } else {
                        stringResource(R.string.printer_no_bonded_devices)
                    }
                )
            }
        } else {
            items(visiblePrinters, key = { it.address }) { printer ->
                BondedPrinterCard(
                    printer = printer,
                    connectionState = state.connectionState,
                    connectedAddress = state.connectedAddress,
                    hasBluetoothPermission = hasBluetoothPermission,
                    bluetoothEnabled = state.bluetoothEnabled,
                    onConnect = { printerManager.connect(printer.address, hasBluetoothPermission) },
                    onDisconnect = printerManager::disconnect
                )
            }
        }
        state.deviceInfo?.let { info ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.printer_device_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrinterInfoRow(
                                label = stringResource(R.string.printer_device_name),
                                value = info.name
                            )
                            info.mac?.let { mac ->
                                PrinterInfoRow(
                                    label = stringResource(R.string.printer_device_mac),
                                    value = mac
                                )
                            }
                            info.batteryPercent?.let { battery ->
                                PrinterInfoRow(
                                    label = stringResource(R.string.printer_device_battery),
                                    value = "$battery%"
                                )
                            }
                            info.standbyMinutes?.let { standbyMinutes ->
                                PrinterInfoRow(
                                    label = stringResource(R.string.printer_device_standby),
                                    value = stringResource(
                                        R.string.printer_device_standby_minutes,
                                        standbyMinutes
                                    )
                                )
                            }
                            info.firmwareVersion?.let { firmwareVersion ->
                                PrinterInfoRow(
                                    label = stringResource(R.string.printer_device_firmware),
                                    value = firmwareVersion
                                )
                            }
                            info.serialNumber?.let { serialNumber ->
                                PrinterInfoRow(
                                    label = stringResource(R.string.printer_device_serial),
                                    value = serialNumber
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxLayerLabelCard(
    positionCode: String,
    onPositionCodeChange: (String) -> Unit,
    partNumber: String,
    onPartNumberChange: (String) -> Unit,
    previewBitmap: Bitmap?,
    printerState: PrinterState,
    onPrint: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.printer_box_label_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = positionCode,
                onValueChange = onPositionCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.printer_box_label_position_label)) },
                placeholder = { Text(text = stringResource(R.string.printer_box_label_position_placeholder)) },
                singleLine = true
            )
            OutlinedTextField(
                value = partNumber,
                onValueChange = onPartNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.printer_box_label_part_label)) },
                placeholder = { Text(text = stringResource(R.string.printer_box_label_part_placeholder)) },
                singleLine = true
            )
            Text(
                text = stringResource(R.string.printer_box_label_preview_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (previewBitmap == null) {
                Text(
                    text = stringResource(R.string.printer_box_label_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.printer_box_label_preview_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
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
            if (printerState.isPrinting) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(text = stringResource(R.string.printer_print_in_progress))
                }
            }
            Button(
                onClick = onPrint,
                enabled = previewBitmap != null &&
                    printerState.connectionState == PrinterConnectionState.CONNECTED &&
                    !printerState.isPrinting
            ) {
                Text(text = stringResource(R.string.printer_print_box_label))
            }
        }
    }
}

@Composable
private fun PrinterTypeOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text = text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text = text)
        }
    }
}

@Composable
private fun BondedPrinterCard(
    printer: BondedPrinter,
    connectionState: PrinterConnectionState,
    connectedAddress: String?,
    hasBluetoothPermission: Boolean,
    bluetoothEnabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isCurrentPrinter = connectedAddress == printer.address
    val isConnected = isCurrentPrinter && connectionState == PrinterConnectionState.CONNECTED
    val isConnecting = isCurrentPrinter && connectionState == PrinterConnectionState.CONNECTING

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = printer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = printer.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp
                    )
                }

                isConnected -> {
                    OutlinedButton(onClick = onDisconnect) {
                        Text(text = stringResource(R.string.printer_disconnect))
                    }
                }

                else -> {
                    Button(
                        onClick = onConnect,
                        enabled = hasBluetoothPermission && bluetoothEnabled
                    ) {
                        Text(text = stringResource(R.string.printer_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrinterInfoRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun hasBluetoothPermission(context: android.content.Context, printerType: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return if (printerType == UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    return bluetoothPermissions(printerType).all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun bluetoothPermissions(printerType: String): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else if (printerType == UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        emptyArray()
    }
}
