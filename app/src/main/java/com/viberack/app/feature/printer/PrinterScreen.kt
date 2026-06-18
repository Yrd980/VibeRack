package com.viberack.app.feature.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viberack.app.R
import com.viberack.app.VibeRackApplication
import com.viberack.app.core.ble.printer.P0BlePrinter
import com.viberack.app.core.ble.printer.P0PrinterStatus

@Composable
fun PrinterRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
    val viewModel: PrinterViewModel = viewModel(factory = PrinterViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetoothPermissions = rememberPrinterBluetoothPermissions()
    var hasBluetoothPermission by remember(bluetoothPermissions) {
        mutableStateOf(hasPrinterBluetoothPermission(context, bluetoothPermissions))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBluetoothPermission = hasPrinterBluetoothPermission(context, bluetoothPermissions)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    PrinterScreen(
        uiState = uiState,
        hasBluetoothPermission = hasBluetoothPermission,
        onPositionChange = viewModel::setPositionCode,
        onPartNumberChange = viewModel::setPartNumber,
        onRequestBluetoothPermission = { permissionLauncher.launch(bluetoothPermissions) },
        onEnableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        onScan = viewModel::scan,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onPrint = viewModel::print,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PrinterScreen(
    uiState: PrinterUiState,
    hasBluetoothPermission: Boolean,
    onPositionChange: (String) -> Unit,
    onPartNumberChange: (String) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onConnect: (P0BlePrinter) -> Unit,
    onDisconnect: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.positionCode,
                        onValueChange = onPositionChange,
                        label = { Text(stringResource(R.string.printer_box_label_position_label)) },
                        placeholder = { Text(stringResource(R.string.printer_box_label_position_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.partNumber,
                        onValueChange = onPartNumberChange,
                        label = { Text(stringResource(R.string.printer_box_label_part_label)) },
                        placeholder = { Text(stringResource(R.string.printer_box_label_part_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.labelError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.printer_box_label_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val preview = uiState.preview
                    if (preview == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(384f / 232f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.printer_box_label_preview_unavailable))
                        }
                    } else {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.printer_box_label_preview_title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(384f / 232f)
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.printer_connection_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(printerStatusText(uiState.printerState.status))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasBluetoothPermission) {
                            Button(onClick = onRequestBluetoothPermission) {
                                Text(stringResource(R.string.printer_request_permission))
                            }
                        }
                        OutlinedButton(onClick = onEnableBluetooth) {
                            Text(stringResource(R.string.printer_enable_bluetooth))
                        }
                        OutlinedButton(onClick = onScan, enabled = hasBluetoothPermission && !uiState.printerState.isScanning) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text(stringResource(R.string.printer_refresh))
                        }
                    }
                    if (uiState.printerState.isConnected) {
                        OutlinedButton(onClick = onDisconnect) {
                            Text(stringResource(R.string.printer_disconnect))
                        }
                    }
                }
            }
        }

        items(uiState.printerState.printers) { printer ->
            PrinterDeviceRow(
                printer = printer,
                onConnect = { onConnect(printer) }
            )
        }

        item {
            Button(
                onClick = onPrint,
                enabled = uiState.printerState.isConnected && !uiState.printerState.isPrinting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null)
                Text(stringResource(R.string.printer_print_box_label))
            }
        }
    }
}

@Composable
private fun PrinterDeviceRow(
    printer: P0BlePrinter,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(printer.name, fontWeight = FontWeight.SemiBold)
                Text(printer.address, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = stringResource(R.string.printer_device_rssi, printer.rssi),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun printerStatusText(status: P0PrinterStatus): String {
    return when (status) {
        P0PrinterStatus.Idle -> stringResource(R.string.printer_status_idle)
        P0PrinterStatus.Disconnected -> stringResource(R.string.printer_status_disconnected)
        P0PrinterStatus.BluetoothUnsupported -> stringResource(R.string.printer_bluetooth_not_supported)
        P0PrinterStatus.PermissionRequired -> stringResource(R.string.printer_permission_required)
        P0PrinterStatus.BluetoothDisabled -> stringResource(R.string.printer_bluetooth_disabled)
        P0PrinterStatus.Scanning -> stringResource(R.string.printer_scan_in_progress)
        P0PrinterStatus.NoMatchingDevices -> stringResource(R.string.printer_no_matching_devices)
        P0PrinterStatus.DiscoverServicesFailed -> stringResource(R.string.printer_discover_services_failed)
        P0PrinterStatus.ServiceMissing -> stringResource(R.string.printer_p0_service_missing)
        P0PrinterStatus.SendFailed -> stringResource(R.string.printer_send_failed)
        P0PrinterStatus.PrintInProgress -> stringResource(R.string.printer_print_in_progress)
        P0PrinterStatus.PrintSuccess -> stringResource(R.string.printer_print_success)
        is P0PrinterStatus.FoundDevice -> stringResource(R.string.printer_status_found_device, status.name)
        is P0PrinterStatus.FoundCount -> stringResource(R.string.printer_status_found_count, status.count)
        is P0PrinterStatus.ScanFailed -> stringResource(R.string.printer_scan_failed_with_code, status.code)
        is P0PrinterStatus.ConnectFailed -> stringResource(R.string.printer_connect_failed_with_code, status.code)
        is P0PrinterStatus.Connected -> stringResource(R.string.printer_status_connected, status.name)
        is P0PrinterStatus.Connecting -> stringResource(R.string.printer_status_connecting, status.name)
        is P0PrinterStatus.SendFailedCode -> stringResource(R.string.printer_send_failed_with_code, status.code)
        is P0PrinterStatus.SendProgress -> stringResource(
            R.string.printer_send_progress,
            status.current,
            status.total
        )
    }
}

@Composable
private fun rememberPrinterBluetoothPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun hasPrinterBluetoothPermission(
    context: android.content.Context,
    permissions: Array<String>
): Boolean {
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
