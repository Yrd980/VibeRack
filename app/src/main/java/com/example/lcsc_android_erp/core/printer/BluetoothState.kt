package com.example.lcsc_android_erp.core.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner

internal fun BluetoothAdapter?.safeIsEnabled(): Boolean {
    return this?.let { adapter ->
        runCatching { adapter.isEnabled }.getOrDefault(false)
    } ?: false
}

internal fun BluetoothAdapter?.safeBluetoothLeScanner(): BluetoothLeScanner? {
    return this?.let { adapter ->
        runCatching { adapter.bluetoothLeScanner }.getOrNull()
    }
}
