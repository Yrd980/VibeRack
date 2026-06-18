package com.viberack.app.core.ble.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

object P0BlePrinterUuids {
    val advertisedService: UUID = UUID.fromString("000018F0-0000-1000-8000-00805F9B34FB")
    val printService: UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    val notifyCharacteristic: UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")
    val writeCharacteristic: UUID = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")
}

data class P0BlePrinter(
    val address: String,
    val name: String,
    val rssi: Int
)

data class P0BlePrinterState(
    val printers: List<P0BlePrinter> = emptyList(),
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isPrinting: Boolean = false,
    val status: P0PrinterStatus = P0PrinterStatus.Idle,
    val connectedName: String? = null
)

sealed interface P0PrinterStatus {
    data object Idle : P0PrinterStatus
    data object Disconnected : P0PrinterStatus
    data object BluetoothUnsupported : P0PrinterStatus
    data object PermissionRequired : P0PrinterStatus
    data object BluetoothDisabled : P0PrinterStatus
    data object Scanning : P0PrinterStatus
    data object NoMatchingDevices : P0PrinterStatus
    data object DiscoverServicesFailed : P0PrinterStatus
    data object ServiceMissing : P0PrinterStatus
    data object SendFailed : P0PrinterStatus
    data object PrintInProgress : P0PrinterStatus
    data object PrintSuccess : P0PrinterStatus
    data class FoundDevice(val name: String) : P0PrinterStatus
    data class FoundCount(val count: Int) : P0PrinterStatus
    data class ScanFailed(val code: Int) : P0PrinterStatus
    data class ConnectFailed(val code: Int) : P0PrinterStatus
    data class Connected(val name: String) : P0PrinterStatus
    data class Connecting(val name: String) : P0PrinterStatus
    data class SendFailedCode(val code: Int) : P0PrinterStatus
    data class SendProgress(val current: Int, val total: Int) : P0PrinterStatus
}

@SuppressLint("MissingPermission")
class P0BlePrinterClient(
    private val appContext: Context,
    private val hasBluetoothPermission: () -> Boolean
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingConnect: CompletableDeferred<Result<Unit>>? = null
    private var pendingWrite: CompletableDeferred<Result<Unit>>? = null

    private val _state = MutableStateFlow(P0BlePrinterState())
    val state: StateFlow<P0BlePrinterState> = _state.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisedName = result.scanRecord?.deviceName ?: result.device.name
            val hasP0Service = result.scanRecord?.serviceUuids?.any {
                it.uuid == P0BlePrinterUuids.advertisedService
            } == true
            if (advertisedName?.let(::isP0Printer) != true && !hasP0Service) return
            val name = advertisedName ?: "P0"
            val printer = P0BlePrinter(
                address = result.device.address.uppercase(Locale.ROOT),
                name = name,
                rssi = result.rssi
            )
            _state.update { current ->
                current.copy(
                    printers = (current.printers.filterNot { it.address == printer.address } + printer)
                        .sortedByDescending { it.rssi },
                    status = P0PrinterStatus.FoundDevice(printer.name)
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    isScanning = false,
                    status = P0PrinterStatus.ScanFailed(errorCode)
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingConnect?.complete(
                    Result.failure(IllegalStateException("connect failed: $status"))
                )
                close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.requestMtu(64) && !gatt.discoverServices()) {
                        pendingConnect?.complete(
                            Result.failure(IllegalStateException("service discovery failed"))
                        )
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> close(P0PrinterStatus.Disconnected)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!gatt.discoverServices()) {
                pendingConnect?.complete(
                    Result.failure(IllegalStateException("service discovery failed"))
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !resolveCharacteristics(gatt.services)) {
                pendingConnect?.complete(
                    Result.failure(IllegalStateException("P0 service missing"))
                )
                return
            }
            _state.update {
                val name = gatt.device.name ?: gatt.device.address
                it.copy(
                    isConnected = true,
                    status = P0PrinterStatus.Connected(name),
                    connectedName = name
                )
            }
            pendingConnect?.complete(Result.success(Unit))
            pendingConnect = null
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingWrite?.complete(
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("send failed: $status"))
                }
            )
            pendingWrite = null
        }
    }

    fun scan(durationMs: Long = 8_000L) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.value = P0BlePrinterState(status = P0PrinterStatus.BluetoothUnsupported)
            return
        }
        if (!hasBluetoothPermission()) {
            _state.update { it.copy(status = P0PrinterStatus.PermissionRequired) }
            return
        }
        if (!adapter.isEnabled) {
            _state.update { it.copy(status = P0PrinterStatus.BluetoothDisabled) }
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.stopScan(scanCallback)
        _state.update {
            it.copy(
                printers = emptyList(),
                isScanning = true,
                status = P0PrinterStatus.Scanning
            )
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        scope.launch {
            delay(durationMs)
            scanner.stopScan(scanCallback)
            _state.update {
                it.copy(
                    isScanning = false,
                    status = if (it.printers.isEmpty()) {
                        P0PrinterStatus.NoMatchingDevices
                    } else {
                        P0PrinterStatus.FoundCount(it.printers.size)
                    }
                )
            }
        }
    }

    suspend fun connect(printer: P0BlePrinter): Result<Unit> = mutex.withLock {
        if (!hasBluetoothPermission()) {
            _state.update { it.copy(status = P0PrinterStatus.PermissionRequired) }
            return@withLock Result.failure(IllegalStateException("permission required"))
        }
        val adapter = bluetoothAdapter
            ?: return@withLock Result.failure(IllegalStateException("bluetooth unsupported"))
        val device = runCatching { adapter.getRemoteDevice(printer.address) }
            .getOrElse {
                return@withLock Result.failure(IllegalStateException("invalid Bluetooth address"))
            }
        close(null)
        val deferred = CompletableDeferred<Result<Unit>>()
        pendingConnect = deferred
        _state.update { it.copy(status = P0PrinterStatus.Connecting(printer.name)) }
        gatt = connectGatt(device)
        withTimeoutOrNull(15_000) { deferred.await() }
            ?: Result.failure(IllegalStateException("connect timed out"))
    }

    suspend fun print(chunks: List<P0PrintChunk>): Result<Unit> = mutex.withLock {
        val gattClient = gatt ?: return@withLock Result.failure(IllegalStateException("not connected"))
        val characteristic = writeCharacteristic
            ?: return@withLock Result.failure(IllegalStateException("write characteristic missing"))
        _state.update { it.copy(isPrinting = true, status = P0PrinterStatus.PrintInProgress) }
        for ((index, chunk) in chunks.withIndex()) {
            pendingWrite = CompletableDeferred()
            if (!write(gattClient, characteristic, chunk.bytes)) {
                _state.update { it.copy(isPrinting = false, status = P0PrinterStatus.SendFailed) }
                return@withLock Result.failure(IllegalStateException("send failed"))
            }
            val result = withTimeoutOrNull(5_000) { pendingWrite?.await() }
                ?: Result.failure(IllegalStateException("send timed out"))
            if (result.isFailure) {
                _state.update { it.copy(isPrinting = false, status = P0PrinterStatus.SendFailed) }
                return@withLock result
            }
            _state.update {
                it.copy(status = P0PrinterStatus.SendProgress(index + 1, chunks.size))
            }
            if (chunk.delayAfterMilliseconds > 0) delay(chunk.delayAfterMilliseconds)
        }
        _state.update { it.copy(isPrinting = false, status = P0PrinterStatus.PrintSuccess) }
        Result.success(Unit)
    }

    fun disconnect() = close(P0PrinterStatus.Disconnected)

    private fun connectGatt(device: BluetoothDevice): BluetoothGatt {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    private fun resolveCharacteristics(services: List<BluetoothGattService>): Boolean {
        val service = services.firstOrNull { it.uuid == P0BlePrinterUuids.printService }
        writeCharacteristic = service?.getCharacteristic(P0BlePrinterUuids.writeCharacteristic)
        return writeCharacteristic != null
    }

    private fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Boolean {
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.writeType = writeType
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun close(status: P0PrinterStatus? = null) {
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        pendingConnect = null
        pendingWrite = null
        if (status != null) {
            _state.update {
                it.copy(isConnected = false, isPrinting = false, connectedName = null, status = status)
            }
        }
    }

    private fun isP0Printer(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        return upper.contains("P0") ||
            name.contains("印立方") ||
            name.contains("德佟") ||
            upper.contains("DETONGER") ||
            upper.contains("DOTHANTECH")
    }
}
