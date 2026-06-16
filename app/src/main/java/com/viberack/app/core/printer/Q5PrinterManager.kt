package com.viberack.app.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import com.viberack.app.R
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Q5PrinterManager(
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
    private var receiveBuffer = ByteArray(0)

    @Volatile
    private var client: Q5BluetoothClient? = null

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
            _state.update {
                it.copy(
                    bluetoothAvailable = true,
                    bluetoothEnabled = bluetoothEnabled,
                    bondedPrinters = emptyList()
                )
            }
            return
        }
        val bondedPrinters = readBondedPrinters(adapter)
        _state.update {
            it.copy(
                bluetoothAvailable = true,
                bluetoothEnabled = bluetoothEnabled,
                bondedPrinters = bondedPrinters
            )
        }
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
        receiveBuffer = ByteArray(0)
        val targetPrinter = _state.value.bondedPrinters.firstOrNull { it.address == normalizedAddress }
        _state.update {
            it.copy(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                connectionState = PrinterConnectionState.CONNECTING,
                connectionSummary = appContext.getString(R.string.printer_status_connecting, targetPrinter?.name ?: normalizedAddress),
                connectedAddress = normalizedAddress,
                connectedName = targetPrinter?.name,
                deviceInfo = null
            )
        }
        client = Q5BluetoothClient(
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
            onBytesReceived = { bytes ->
                scope.launch {
                    handleIncomingBytes(bytes)
                }
            }
        ).also { it.connect(normalizedAddress) }
    }

    override fun disconnect() {
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
                Q5ImageEncoder.buildBitmapPrintChunks(bitmap).forEach { chunk ->
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
    private fun readBondedPrinters(adapter: BluetoothAdapter): List<BondedPrinter> {
        return adapter.bondedDevices
            .orEmpty()
            .map { device ->
                BondedPrinter(
                    name = device.name ?: appContext.getString(R.string.printer_unknown_device),
                    address = device.address
                )
            }
            .sortedWith(
                compareBy<BondedPrinter>(
                    { !PrinterNameMatcher.isDeliQ5(it.name) },
                    { it.name },
                    { it.address }
                )
            )
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
                        connectedName = targetName
                    )
                }
                queryDeviceInfo()
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

    private fun handleIncomingBytes(bytes: ByteArray) {
        receiveBuffer += bytes
        val (frames, remaining) = Q5Protocol.tryExtractFrames(receiveBuffer)
        receiveBuffer = remaining
        frames.forEach { frame ->
            if (Q5Protocol.isAck(frame)) {
                return@forEach
            }
            when (frame.command) {
                0x35 -> {
                    val deviceInfo = Q5Protocol.parseDeviceInfo(frame) ?: return@forEach
                    _state.update {
                        it.copy(
                            connectedName = deviceInfo.name,
                            deviceInfo = PrinterDeviceInfo(
                                name = deviceInfo.name,
                                mac = deviceInfo.mac,
                                standbyMinutes = deviceInfo.standbyMinutes,
                                batteryPercent = deviceInfo.batteryPercent,
                                firmwareVersion = deviceInfo.firmwareVersion,
                                serialNumber = deviceInfo.serialNumber,
                            ),
                            connectionSummary = appContext.getString(R.string.printer_status_connected, deviceInfo.name)
                        )
                    }
                }
            }
        }
    }

    private fun queryDeviceInfo() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                client?.send(Q5Protocol.queryDeviceInfo)
            }
        }
    }
}
