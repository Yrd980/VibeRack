package com.viberack.app.core.ble.smart

import kotlinx.coroutines.flow.StateFlow

interface SmartChassisClient {
    val discoveredChassis: StateFlow<List<SmartChassisDevice>>
    val connectionState: StateFlow<SmartChassisConnectionState>
    val tableInfoUpdates: StateFlow<SmartChassisTableInfo?>

    suspend fun startScan(): SmartChassisClientResult<List<SmartChassisDevice>>
    suspend fun stopScan(): SmartChassisClientResult<Unit>
    suspend fun connect(address: String): SmartChassisClientResult<SmartChassisDevice>
    suspend fun disconnect(): SmartChassisClientResult<Unit>
    suspend fun readTableInfo(): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun readOne(slot: Int): SmartChassisClientResult<SmartChassisSlotRecord>
    suspend fun readAll(): SmartChassisClientResult<SmartChassisTableSnapshot>
    suspend fun readDeviceHealth(): SmartChassisClientResult<SmartChassisDeviceHealth>
    suspend fun writeOne(record: SmartChassisSlotRecord): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun clearOne(slot: Int): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun insertAt(slot: Int, record: SmartChassisSlotRecord): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun removeAt(slot: Int): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun moveBlock(from: Int, to: Int, length: Int): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun setQuantity(slot: Int, quantity: Int): SmartChassisClientResult<SmartChassisTableInfo>
    suspend fun sendLightCommand(command: SmartChassisLightCommand): SmartChassisClientResult<SmartChassisLightStatus>
}

data class SmartChassisDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val advertisement: SmartChassisAdvertisement,
    val lastSeenAt: Long = System.currentTimeMillis()
) {
    val isSupportedProtocol: Boolean
        get() = advertisement.protoVersion == SmartChassisProtocol.PROTOCOL_VERSION

    val requiresAppUpgrade: Boolean
        get() = advertisement.protoVersion > SmartChassisProtocol.PROTOCOL_VERSION

    val requiresFirmwareUpgrade: Boolean
        get() = advertisement.protoVersion < SmartChassisProtocol.PROTOCOL_VERSION
}

data class SmartChassisTableSnapshot(
    val records: List<SmartChassisSlotRecord>,
    val tableInfo: SmartChassisTableInfo
)

data class SmartChassisConnectionState(
    val phase: SmartChassisConnectionPhase = SmartChassisConnectionPhase.DISCONNECTED,
    val device: SmartChassisDevice? = null,
    val message: String? = null
) {
    val isConnected: Boolean
        get() = phase == SmartChassisConnectionPhase.CONNECTED && device != null
}

enum class SmartChassisConnectionPhase {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

sealed class SmartChassisClientResult<out T> {
    data class Success<T>(
        val value: T,
        val op: SmartChassisBindingOp? = null,
        val status: SmartChassisBindingStatus = SmartChassisBindingStatus.OK
    ) : SmartChassisClientResult<T>()

    data class Failure(
        val message: String,
        val op: SmartChassisBindingOp? = null,
        val status: SmartChassisBindingStatus? = null
    ) : SmartChassisClientResult<Nothing>()
}

data class SmartChassisOperationError(
    val op: SmartChassisBindingOp?,
    val status: SmartChassisBindingStatus?,
    val message: String
)
