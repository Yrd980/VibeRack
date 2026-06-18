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
    val statusMessage: String = "未连接",
    val connectedName: String? = null
)

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
            val name = result.scanRecord?.deviceName ?: result.device.name ?: "未知 P0"
            if (!isP0Printer(name)) return
            val printer = P0BlePrinter(
                address = result.device.address.uppercase(Locale.ROOT),
                name = name,
                rssi = result.rssi
            )
            _state.update { current ->
                current.copy(
                    printers = (current.printers.filterNot { it.address == printer.address } + printer)
                        .sortedByDescending { it.rssi },
                    statusMessage = "发现 ${printer.name}"
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(isScanning = false, statusMessage = "扫描失败：$errorCode") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingConnect?.complete(Result.failure(IllegalStateException("连接失败：$status")))
                close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.requestMtu(64) && !gatt.discoverServices()) {
                        pendingConnect?.complete(Result.failure(IllegalStateException("发现服务启动失败")))
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> close("已断开")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!gatt.discoverServices()) {
                pendingConnect?.complete(Result.failure(IllegalStateException("发现服务启动失败")))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !resolveCharacteristics(gatt.services)) {
                pendingConnect?.complete(Result.failure(IllegalStateException("未找到 P0 打印服务")))
                return
            }
            _state.update {
                it.copy(
                    isConnected = true,
                    statusMessage = "已连接 ${gatt.device.name ?: gatt.device.address}",
                    connectedName = gatt.device.name ?: gatt.device.address
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
                    Result.failure(IllegalStateException("发送失败：$status"))
                }
            )
            pendingWrite = null
        }
    }

    fun scan(durationMs: Long = 8_000L) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.value = P0BlePrinterState(statusMessage = "当前设备不支持蓝牙")
            return
        }
        if (!hasBluetoothPermission()) {
            _state.update { it.copy(statusMessage = "需要蓝牙权限") }
            return
        }
        if (!adapter.isEnabled) {
            _state.update { it.copy(statusMessage = "蓝牙未开启") }
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.stopScan(scanCallback)
        _state.update { it.copy(printers = emptyList(), isScanning = true, statusMessage = "正在扫描 P0 打印机...") }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        scope.launch {
            delay(durationMs)
            scanner.stopScan(scanCallback)
            _state.update {
                it.copy(
                    isScanning = false,
                    statusMessage = if (it.printers.isEmpty()) "未发现 P0 打印机" else "发现 ${it.printers.size} 台 P0 打印机"
                )
            }
        }
    }

    suspend fun connect(printer: P0BlePrinter): Result<Unit> = mutex.withLock {
        if (!hasBluetoothPermission()) return@withLock Result.failure(IllegalStateException("需要蓝牙权限"))
        val adapter = bluetoothAdapter ?: return@withLock Result.failure(IllegalStateException("当前设备不支持蓝牙"))
        val device = runCatching { adapter.getRemoteDevice(printer.address) }
            .getOrElse { return@withLock Result.failure(IllegalStateException("无效蓝牙地址")) }
        close(null)
        val deferred = CompletableDeferred<Result<Unit>>()
        pendingConnect = deferred
        _state.update { it.copy(statusMessage = "正在连接 ${printer.name}...") }
        gatt = connectGatt(device)
        withTimeoutOrNull(15_000) { deferred.await() }
            ?: Result.failure(IllegalStateException("连接超时"))
    }

    suspend fun print(chunks: List<P0PrintChunk>): Result<Unit> = mutex.withLock {
        val gattClient = gatt ?: return@withLock Result.failure(IllegalStateException("请先连接 P0 打印机"))
        val characteristic = writeCharacteristic ?: return@withLock Result.failure(IllegalStateException("未找到 P0 写入特征"))
        _state.update { it.copy(isPrinting = true, statusMessage = "正在发送标签...") }
        for ((index, chunk) in chunks.withIndex()) {
            pendingWrite = CompletableDeferred()
            if (!write(gattClient, characteristic, chunk.bytes)) {
                _state.update { it.copy(isPrinting = false, statusMessage = "发送失败") }
                return@withLock Result.failure(IllegalStateException("发送失败"))
            }
            val result = withTimeoutOrNull(5_000) { pendingWrite?.await() }
                ?: Result.failure(IllegalStateException("发送超时"))
            if (result.isFailure) {
                _state.update { it.copy(isPrinting = false, statusMessage = result.exceptionOrNull()?.message ?: "发送失败") }
                return@withLock result
            }
            _state.update { it.copy(statusMessage = "正在发送标签 ${index + 1}/${chunks.size}") }
            if (chunk.delayAfterMilliseconds > 0) delay(chunk.delayAfterMilliseconds)
        }
        _state.update { it.copy(isPrinting = false, statusMessage = "标签已发送") }
        Result.success(Unit)
    }

    fun disconnect() = close("已断开")

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

    private fun close(message: String? = null) {
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        pendingConnect = null
        pendingWrite = null
        if (message != null) {
            _state.update {
                it.copy(isConnected = false, isPrinting = false, connectedName = null, statusMessage = message)
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
