package com.viberack.app.core.printer

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

data class BondedPrinter(
    val name: String,
    val address: String,
)

enum class PrinterConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

data class PrinterDeviceInfo(
    val name: String,
    val mac: String? = null,
    val standbyMinutes: Int? = null,
    val batteryPercent: Int? = null,
    val firmwareVersion: String? = null,
    val serialNumber: String? = null,
)

data class PrinterState(
    val bluetoothAvailable: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bondedPrinters: List<BondedPrinter> = emptyList(),
    val connectionState: PrinterConnectionState = PrinterConnectionState.DISCONNECTED,
    val connectionSummary: String = "",
    val connectedAddress: String? = null,
    val connectedName: String? = null,
    val deviceInfo: PrinterDeviceInfo? = null,
    val isPrinting: Boolean = false,
)

interface PrinterManager {
    val state: StateFlow<PrinterState>

    fun refreshBondedPrinters(hasBluetoothPermission: Boolean)
    fun connect(address: String, hasBluetoothPermission: Boolean)
    fun disconnect()
    fun printBitmap(bitmap: Bitmap, onCompleted: (String?) -> Unit)
}
