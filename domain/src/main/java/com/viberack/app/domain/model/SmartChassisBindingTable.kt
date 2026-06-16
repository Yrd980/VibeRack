package com.viberack.app.domain.model

data class SmartChassisSlotRecord(
    val slot: Int,
    val partId: String,
    val quantity: Int,
    val flags: Int,
    val crc8: Int
) {
    val isEmpty: Boolean get() = slot == 0 || partId.isBlank()
}

data class SmartChassisTableInfo(
    val tableSeq: Long,
    val crc16: Int,
    val slotCount: Int
)
