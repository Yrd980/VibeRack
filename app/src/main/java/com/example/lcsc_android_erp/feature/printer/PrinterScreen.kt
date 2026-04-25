package com.example.lcsc_android_erp.feature.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.datastore.UserPreferences
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.core.printer.BondedPrinter
import com.example.lcsc_android_erp.core.printer.PrinterConnectionState
import com.example.lcsc_android_erp.core.printer.Q5PrinterManager
import kotlinx.coroutines.launch

@Composable
fun PrinterRoute(
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as LcscApplication).appContainer
    val preferences by appContainer.userPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val coroutineScope = rememberCoroutineScope()
    PrinterScreen(
        printerManager = appContainer.q5PrinterManager,
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
    printerManager: Q5PrinterManager,
    printerType: String,
    onPrinterTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by printerManager.state.collectAsStateWithLifecycle()
    var hasBluetoothPermission by rememberSaveable {
        mutableStateOf(hasBluetoothPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBluetoothPermission = hasBluetoothPermission(context)
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }

    LaunchedEffect(hasBluetoothPermission) {
        printerManager.refreshBondedPrinters(hasBluetoothPermission)
    }

    DisposableEffect(lifecycleOwner, printerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBluetoothPermission = hasBluetoothPermission(context)
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
                printer.name.contains("Q5", ignoreCase = true) ||
                    printer.name.contains("DELI", ignoreCase = true) ||
                    printer.name.contains("得力", ignoreCase = true)
            }

            else -> state.bondedPrinters
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
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
                    OutlinedButton(
                        onClick = printerManager::disconnect
                    ) {
                        Text(text = stringResource(R.string.printer_disconnect))
                    }
                }
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
                    body = if (printerType == UserPreferencesRepository.PRINTER_TYPE_DELI_Q5) {
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
                            PrinterInfoRow(
                                label = stringResource(R.string.printer_device_mac),
                                value = info.mac
                            )
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
                            PrinterInfoRow(
                                label = stringResource(R.string.printer_device_firmware),
                                value = info.firmwareVersion
                            )
                            PrinterInfoRow(
                                label = stringResource(R.string.printer_device_serial),
                                value = info.serialNumber
                            )
                        }
                    }
                }
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

private fun hasBluetoothPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
}
