package com.viberack.app.core.ble.smart

data class SmartChassisAdvertisement(
    val companyId: Int,
    val protoVersion: Int,
    val batchId: Int,
    val batteryPct: Int,
    val statusFlags: Int,
    val tableSeqLow16: Int
)

typealias SmartChassisSlotRecord = com.viberack.app.domain.model.SmartChassisSlotRecord

typealias SmartChassisTableInfo = com.viberack.app.domain.model.SmartChassisTableInfo

data class SmartChassisBindingResult(
    val op: SmartChassisBindingOp,
    val rawOp: Int,
    val status: SmartChassisBindingStatus,
    val rawStatus: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmartChassisBindingResult) return false
        return op == other.op &&
            rawOp == other.rawOp &&
            status == other.status &&
            rawStatus == other.rawStatus &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = op.hashCode()
        result = 31 * result + rawOp
        result = 31 * result + status.hashCode()
        result = 31 * result + rawStatus
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class SmartChassisLightCommand(
    val mode: SmartChassisLightMode,
    val maskA: Int,
    val maskB: Int = 0,
    val colorA: RgbColor,
    val colorB: RgbColor = RgbColor(0, 0, 0),
    val timeoutSeconds: Int = 0
)

data class SmartChassisLightStatus(
    val mode: SmartChassisLightMode,
    val rawMode: Int,
    val remainingSeconds: Int
)

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int
)
