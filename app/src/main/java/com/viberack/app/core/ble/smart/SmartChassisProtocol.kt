package com.viberack.app.core.ble.smart

object SmartChassisProtocol {
    const val PROTOCOL_VERSION: Int = 0x01
    const val SLOT_COUNT: Int = 25
    const val SLOT_RECORD_SIZE: Int = 16
    const val LIGHT_COMMAND_SIZE: Int = 17
    const val TABLE_INFO_SIZE: Int = 7
    const val DEVICE_HEALTH_SIZE: Int = 4
    const val READ_ALL_END_MARKER: Int = 0xFF
    const val DEV_COMPANY_ID: Int = 0xFFFF
    const val FACTORY_RESET_MAGIC: Long = 0x5A5AA5A5L
    const val ADVERTISEMENT_CORE_SIZE: Int = 9
    const val ADVERTISEMENT_FIRMWARE_SIZE: Int = 11
    const val ANDROID_MANUFACTURER_PAYLOAD_CORE_SIZE: Int = 7
    const val ANDROID_MANUFACTURER_PAYLOAD_FIRMWARE_SIZE: Int = 9
    const val SLOT_MASK_MAX: Int = 0x01FF_FFFF
    const val DEFAULT_LIGHT_TIMEOUT_SECONDS: Int = 30
    const val MAX_LIGHT_TIMEOUT_SECONDS: Int = 300
    const val MAX_FX_TIMEOUT_SECONDS: Int = 10

    const val ADV_LOW_BATTERY: Int = 1 shl 0
    const val ADV_HAS_UNBOUND_SLOT: Int = 1 shl 1
    const val ADV_LIGHT_ACTIVE: Int = 1 shl 2
    const val ADV_FAULT: Int = 1 shl 3

    const val SLOT_FLAG_MSD: Int = 1 shl 0
    const val SLOT_FLAG_LOW_STOCK: Int = 1 shl 1
    const val SLOT_FLAG_CUSTOM_PART: Int = 1 shl 2
}

enum class SmartChassisBindingStatus(val code: Int) {
    OK(0x00),
    ERR_PARAM(0x01),
    ERR_FULL(0x02),
    ERR_FLASH_BUSY(0x03),
    ERR_CRC(0x04),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): SmartChassisBindingStatus {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

enum class SmartChassisBindingOp(val code: Int) {
    READ_ONE(0x01),
    READ_ALL(0x02),
    WRITE_ONE(0x10),
    CLEAR_ONE(0x11),
    INSERT_AT(0x20),
    REMOVE_AT(0x21),
    MOVE_BLOCK(0x22),
    SET_QTY(0x30),
    FACTORY_RESET(0xF0),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): SmartChassisBindingOp {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

enum class SmartChassisLightMode(val code: Int) {
    OFF(0x00),
    FIND(0x01),
    PICK(0x02),
    SORT(0x03),
    STOCK_IN(0x04),
    FX(0x05),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): SmartChassisLightMode {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
