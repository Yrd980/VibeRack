package com.viberack.app.core.ble.smart

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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

class SmartChassisScanner(
    appContext: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SmartChassisScannerState())
    val state: StateFlow<SmartChassisScannerState> = _state.asStateFlow()

    private var scanJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val name = record.deviceName ?: result.device.name
            if (!isSmartChassisName(name)) {
                return
            }
            val advertisement = parseAdvertisement(record) ?: return
            val address = result.device.address.uppercase(Locale.ROOT)
            val device = SmartChassisDevice(
                address = address,
                name = name,
                rssi = result.rssi,
                advertisement = advertisement,
                lastSeenAt = System.currentTimeMillis()
            )
            _state.update { current ->
                current.copy(
                    devices = (current.devices.filterNot { it.address == address } + device)
                        .sortedBy { it.name ?: it.address },
                    lastError = null
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    isScanning = false,
                    lastError = "BLE scan failed: $errorCode"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(
        hasBluetoothScanPermission: Boolean,
        durationMs: Long = DEFAULT_SCAN_DURATION_MS
    ) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.value = SmartChassisScannerState(
                bluetoothAvailable = false,
                bluetoothEnabled = false,
                lastError = "Bluetooth is not supported"
            )
            return
        }
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!hasBluetoothScanPermission) {
            stopScan()
            _state.value = SmartChassisScannerState(
                bluetoothAvailable = true,
                bluetoothEnabled = enabled,
                lastError = "Bluetooth scan permission is required"
            )
            return
        }
        if (!enabled) {
            stopScan()
            _state.value = SmartChassisScannerState(
                bluetoothAvailable = true,
                bluetoothEnabled = false,
                lastError = "Bluetooth is disabled"
            )
            return
        }
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull()
        if (scanner == null) {
            _state.value = SmartChassisScannerState(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                lastError = "BLE scanner is unavailable"
            )
            return
        }
        stopScan()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        _state.value = SmartChassisScannerState(
            bluetoothAvailable = true,
            bluetoothEnabled = true,
            isScanning = true
        )
        scanner.startScan(null, settings, scanCallback)
        scanJob = scope.launch {
            delay(durationMs)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        runCatching {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        _state.update { current ->
            if (current.isScanning) {
                current.copy(isScanning = false)
            } else {
                current
            }
        }
    }

    private fun parseAdvertisement(record: ScanRecord): SmartChassisAdvertisement? {
        val payload = record.getManufacturerSpecificData(SmartChassisProtocol.DEV_COMPANY_ID)
        if (payload != null) {
            return SmartChassisCodec.parseAndroidManufacturerPayload(
                companyId = SmartChassisProtocol.DEV_COMPANY_ID,
                payload = payload
            )
        }
        return SmartChassisCodec.parseAdvertisementFromManufacturerData(record)
    }

    private fun isSmartChassisName(name: String?): Boolean {
        return name
            ?.uppercase(Locale.ROOT)
            ?.matches(SMART_CHASSIS_NAME_REGEX)
            ?: false
    }

    companion object {
        private const val DEFAULT_SCAN_DURATION_MS = 8_000L
        private val SMART_CHASSIS_NAME_REGEX = Regex("^VBRK-[0-9A-F]{4}$")
    }
}

data class SmartChassisScannerState(
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<SmartChassisDevice> = emptyList(),
    val lastError: String? = null
)
