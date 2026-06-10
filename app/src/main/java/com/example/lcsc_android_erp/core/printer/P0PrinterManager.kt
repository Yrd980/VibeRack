package com.example.lcsc_android_erp.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Bitmap
import com.example.lcsc_android_erp.R
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class P0PrinterManager(
    private val appContext: Context
) : PrinterManager {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        PrinterState(
            bluetoothAvailable = bluetoothAdapter != null,
            bluetoothEnabled = bluetoothAdapter.safeIsEnabled(),
            connectionSummary = appContext.getString(R.string.printer_status_idle)
        )
    )
    override val state: StateFlow<PrinterState> = _state.asStateFlow()

    @Volatile
    private var client: P0BluetoothClient? = null

    private var scanJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName
                ?: result.device.name
                ?: appContext.getString(R.string.printer_unknown_device)
            val address = result.device.address.uppercase(Locale.ROOT)
            val isP0Printer = PrinterNameMatcher.isDetongerP0(name)
            if (!isP0Printer) {
                return
            }
            _state.update { state ->
                val existing = state.bondedPrinters.filterNot { it.address == address }
                state.copy(
                    bondedPrinters = (existing + BondedPrinter(name, address))
                        .sortedWith(compareBy({ !PrinterNameMatcher.isDetongerP0(it.name) }, { it.name }, { it.address }))
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    connectionSummary = appContext.getString(R.string.printer_scan_failed)
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun refreshBondedPrinters(hasBluetoothPermission: Boolean) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update {
                it.copy(
                    bluetoothAvailable = false,
                    bluetoothEnabled = false,
                    bondedPrinters = emptyList(),
                    connectionSummary = appContext.getString(R.string.printer_bluetooth_not_supported)
                )
            }
            return
        }
        val bluetoothEnabled = adapter.safeIsEnabled()
        if (!hasBluetoothPermission) {
            stopScan()
            _state.update {
                it.copy(
                    bluetoothAvailable = true,
                    bluetoothEnabled = bluetoothEnabled,
                    bondedPrinters = emptyList()
                )
            }
            return
        }
        if (!bluetoothEnabled) {
            stopScan()
            _state.update {
                it.copy(
                    bluetoothAvailable = true,
                    bluetoothEnabled = false,
                    bondedPrinters = emptyList(),
                    connectionSummary = appContext.getString(R.string.printer_bluetooth_disabled)
                )
            }
            return
        }
        _state.update {
            it.copy(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                bondedPrinters = emptyList(),
                connectionSummary = appContext.getString(R.string.printer_scan_in_progress)
            )
        }
        startScan(adapter)
    }

    @SuppressLint("MissingPermission")
    override fun connect(address: String, hasBluetoothPermission: Boolean) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update {
                it.copy(
                    connectionState = PrinterConnectionState.DISCONNECTED,
                    connectionSummary = appContext.getString(R.string.printer_bluetooth_not_supported)
                )
            }
            return
        }
        if (!hasBluetoothPermission) {
            _state.update {
                it.copy(
                    connectionState = PrinterConnectionState.DISCONNECTED,
                    connectionSummary = appContext.getString(R.string.printer_permission_required)
                )
            }
            return
        }
        if (!adapter.safeIsEnabled()) {
            _state.update {
                it.copy(
                    bluetoothEnabled = false,
                    connectionState = PrinterConnectionState.DISCONNECTED,
                    connectionSummary = appContext.getString(R.string.printer_bluetooth_disabled)
                )
            }
            return
        }
        val normalizedAddress = address.trim().uppercase(Locale.ROOT)
        if (normalizedAddress.isBlank()) {
            _state.update {
                it.copy(
                    connectionState = PrinterConnectionState.DISCONNECTED,
                    connectionSummary = appContext.getString(R.string.printer_connect_address_required)
                )
            }
            return
        }
        stopScan()
        val targetPrinter = _state.value.bondedPrinters.firstOrNull { it.address == normalizedAddress }
        _state.update {
            it.copy(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                connectionState = PrinterConnectionState.CONNECTING,
                connectionSummary = appContext.getString(
                    R.string.printer_status_connecting,
                    targetPrinter?.name ?: normalizedAddress
                ),
                connectedAddress = normalizedAddress,
                connectedName = targetPrinter?.name,
                deviceInfo = null
            )
        }
        client = P0BluetoothClient(
            adapter = adapter,
            onStateChanged = { connectionState, message ->
                scope.launch {
                    handleConnectionStateChanged(
                        connectionState = connectionState,
                        message = message,
                        address = normalizedAddress,
                        fallbackName = targetPrinter?.name
                    )
                }
            },
            onBytesReceived = {}
        ).also { it.connect(normalizedAddress) }
    }

    override fun disconnect() {
        stopScan()
        client?.disconnect()
        client = null
        _state.update {
            it.copy(
                bluetoothEnabled = bluetoothAdapter.safeIsEnabled(),
                connectionState = PrinterConnectionState.DISCONNECTED,
                connectionSummary = appContext.getString(R.string.printer_status_disconnected),
                connectedAddress = null,
                connectedName = null,
                deviceInfo = null,
                isPrinting = false
            )
        }
    }

    override fun printBitmap(bitmap: Bitmap, onCompleted: (String?) -> Unit) {
        val currentClient = client
        if (currentClient == null || _state.value.connectionState != PrinterConnectionState.CONNECTED) {
            onCompleted(appContext.getString(R.string.printer_not_connected))
            return
        }
        if (_state.value.isPrinting) {
            onCompleted(appContext.getString(R.string.printer_print_in_progress))
            return
        }
        _state.update { it.copy(isPrinting = true) }
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                P0Protocol.buildBitmapPrintChunks(bitmap).forEach { chunk ->
                    if (chunk.bytes.isNotEmpty()) {
                        currentClient.send(chunk.bytes)
                    }
                    if (chunk.delayAfterMs > 0) {
                        Thread.sleep(chunk.delayAfterMs)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(isPrinting = false) }
                onCompleted(result.exceptionOrNull()?.message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan(adapter: BluetoothAdapter) {
        stopScan()
        val scanner = adapter.safeBluetoothLeScanner()
        if (scanner == null) {
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        scanJob = scope.launch {
            delay(8_000)
            stopScan()
            _state.update { state ->
                if (state.connectionState == PrinterConnectionState.DISCONNECTED) {
                    state.copy(connectionSummary = appContext.getString(R.string.printer_status_idle))
                } else {
                    state
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        bluetoothAdapter.safeBluetoothLeScanner()?.let { scanner ->
            runCatching {
                scanner.stopScan(scanCallback)
            }
        }
    }

    private fun handleConnectionStateChanged(
        connectionState: PrinterConnectionState,
        message: String,
        address: String,
        fallbackName: String?
    ) {
        when (connectionState) {
            PrinterConnectionState.CONNECTING -> {
                val targetName = fallbackName ?: _state.value.connectedName ?: address
                _state.update {
                    it.copy(
                        connectionState = connectionState,
                        connectionSummary = appContext.getString(
                            R.string.printer_status_connecting,
                            targetName
                        ),
                        connectedAddress = address,
                        connectedName = targetName
                    )
                }
            }

            PrinterConnectionState.CONNECTED -> {
                val targetName = fallbackName ?: _state.value.connectedName ?: address
                _state.update {
                    it.copy(
                        bluetoothEnabled = bluetoothAdapter.safeIsEnabled(),
                        connectionState = connectionState,
                        connectionSummary = appContext.getString(
                            R.string.printer_status_connected,
                            targetName
                        ),
                        connectedAddress = address,
                        connectedName = targetName,
                        deviceInfo = PrinterDeviceInfo(
                            name = targetName,
                            mac = address,
                        )
                    )
                }
            }

            PrinterConnectionState.DISCONNECTED -> {
                val summaryRes = if (message.contains("failed", ignoreCase = true)) {
                    R.string.printer_status_connect_failed
                } else {
                    R.string.printer_status_disconnected
                }
                _state.update {
                    it.copy(
                        bluetoothEnabled = bluetoothAdapter.safeIsEnabled(),
                        connectionState = connectionState,
                        connectionSummary = appContext.getString(summaryRes),
                        connectedAddress = null,
                        connectedName = null,
                        deviceInfo = null,
                        isPrinting = false
                    )
                }
            }
        }
    }
}
