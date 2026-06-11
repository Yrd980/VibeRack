package com.example.lcsc_android_erp.core.ble.smart

import java.nio.charset.StandardCharsets
import java.util.Locale

object SmartChassisCodec {
    fun parseAdvertisement(manufacturerData: ByteArray): SmartChassisAdvertisement? {
        if (manufacturerData.size < SmartChassisProtocol.ADVERTISEMENT_CORE_SIZE) {
            return null
        }
        return SmartChassisAdvertisement(
            companyId = manufacturerData.u16Le(0),
            protoVersion = manufacturerData.u8(2),
            batchId = manufacturerData.u16Le(3),
            batteryPct = manufacturerData.u8(5),
            statusFlags = manufacturerData.u8(6),
            tableSeqLow16 = manufacturerData.u16Le(7)
        )
    }

    fun parseAndroidManufacturerPayload(
        companyId: Int,
        payload: ByteArray
    ): SmartChassisAdvertisement? {
        if (payload.size < SmartChassisProtocol.ANDROID_MANUFACTURER_PAYLOAD_CORE_SIZE) {
            return null
        }
        return SmartChassisAdvertisement(
            companyId = companyId,
            protoVersion = payload.u8(0),
            batchId = payload.u16Le(1),
            batteryPct = payload.u8(3),
            statusFlags = payload.u8(4),
            tableSeqLow16 = payload.u16Le(5)
        )
    }

    fun encodeSlotRecord(
        slot: Int,
        partId: String,
        quantity: Int,
        flags: Int
    ): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        require(quantity in 0..0xFFFF) { "quantity must fit uint16" }
        require(flags in 0..0xFF) { "flags must fit uint8" }
        val normalizedPartId = partId.trim().uppercase(Locale.ROOT)
        require(normalizedPartId.length <= 10) { "part_id must be at most 10 ASCII bytes" }
        require(normalizedPartId.all { it.code in 0x21..0x7E }) { "part_id must be printable ASCII" }

        val bytes = ByteArray(SmartChassisProtocol.SLOT_RECORD_SIZE)
        bytes[0] = slot.toByte()
        val partBytes = normalizedPartId.toByteArray(StandardCharsets.US_ASCII)
        partBytes.copyInto(bytes, destinationOffset = 1, endIndex = partBytes.size)
        bytes.putU16Le(11, quantity)
        bytes[13] = flags.toByte()
        bytes[14] = 0
        bytes[15] = crc8Maxim(bytes, 0, SmartChassisProtocol.SLOT_RECORD_SIZE - 1).toByte()
        return bytes
    }

    fun parseSlotRecord(bytes: ByteArray, requireValidCrc: Boolean = true): SmartChassisSlotRecord? {
        if (bytes.size != SmartChassisProtocol.SLOT_RECORD_SIZE) {
            return null
        }
        val crc = bytes.u8(15)
        val computed = crc8Maxim(bytes, 0, SmartChassisProtocol.SLOT_RECORD_SIZE - 1)
        if (requireValidCrc && crc != computed) {
            return null
        }
        return SmartChassisSlotRecord(
            slot = bytes.u8(0),
            partId = bytes.decodePartId(),
            quantity = bytes.u16Le(11),
            flags = bytes.u8(13),
            crc8 = crc
        )
    }

    fun encodeReadOne(slot: Int): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        return byteArrayOf(SmartChassisBindingOp.READ_ONE.code.toByte(), slot.toByte())
    }

    fun encodeReadAll(): ByteArray {
        return byteArrayOf(SmartChassisBindingOp.READ_ALL.code.toByte())
    }

    fun encodeWriteOne(record: ByteArray): ByteArray {
        require(record.size == SmartChassisProtocol.SLOT_RECORD_SIZE) { "record must be 16 bytes" }
        require(record.u8(0) in 1..SmartChassisProtocol.SLOT_COUNT) { "record slot must be 1..25" }
        return byteArrayOf(SmartChassisBindingOp.WRITE_ONE.code.toByte()) + record
    }

    fun encodeClearOne(slot: Int): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        return byteArrayOf(SmartChassisBindingOp.CLEAR_ONE.code.toByte(), slot.toByte())
    }

    fun encodeInsertAt(slot: Int, record: ByteArray): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        require(record.size == SmartChassisProtocol.SLOT_RECORD_SIZE) { "record must be 16 bytes" }
        require(record.u8(0) in 1..SmartChassisProtocol.SLOT_COUNT) { "record slot must be 1..25" }
        return byteArrayOf(SmartChassisBindingOp.INSERT_AT.code.toByte(), slot.toByte()) + record
    }

    fun encodeRemoveAt(slot: Int): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        return byteArrayOf(SmartChassisBindingOp.REMOVE_AT.code.toByte(), slot.toByte())
    }

    fun encodeMoveBlock(from: Int, to: Int, length: Int): ByteArray {
        require(from in 1..SmartChassisProtocol.SLOT_COUNT) { "from must be 1..25" }
        require(to in 1..SmartChassisProtocol.SLOT_COUNT) { "to must be 1..25" }
        require(length in 1..SmartChassisProtocol.SLOT_COUNT) { "length must be 1..25" }
        require(from + length - 1 <= SmartChassisProtocol.SLOT_COUNT) { "from + length must stay within 25 slots" }
        require(to + length - 1 <= SmartChassisProtocol.SLOT_COUNT) { "to + length must stay within 25 slots" }
        return byteArrayOf(
            SmartChassisBindingOp.MOVE_BLOCK.code.toByte(),
            from.toByte(),
            to.toByte(),
            length.toByte()
        )
    }

    fun encodeSetQuantity(slot: Int, quantity: Int): ByteArray {
        require(slot in 1..SmartChassisProtocol.SLOT_COUNT) { "slot must be 1..25" }
        require(quantity in 0..0xFFFF) { "quantity must fit uint16" }
        return ByteArray(4).also { bytes ->
            bytes[0] = SmartChassisBindingOp.SET_QTY.code.toByte()
            bytes[1] = slot.toByte()
            bytes.putU16Le(2, quantity)
        }
    }

    fun encodeFactoryReset(): ByteArray {
        return ByteArray(5).also { bytes ->
            bytes[0] = SmartChassisBindingOp.FACTORY_RESET.code.toByte()
            bytes.putU32Le(1, SmartChassisProtocol.FACTORY_RESET_MAGIC)
        }
    }

    fun parseBindingResult(bytes: ByteArray): SmartChassisBindingResult? {
        if (bytes.size < 2) {
            return null
        }
        val rawOp = bytes.u8(0)
        val rawStatus = bytes.u8(1)
        return SmartChassisBindingResult(
            op = SmartChassisBindingOp.fromCode(rawOp),
            rawOp = rawOp,
            status = SmartChassisBindingStatus.fromCode(rawStatus),
            rawStatus = rawStatus,
            payload = bytes.copyOfRange(2, bytes.size)
        )
    }

    fun isReadAllEndPayload(payload: ByteArray): Boolean {
        return payload.size == 1 && payload.u8(0) == SmartChassisProtocol.READ_ALL_END_MARKER
    }

    fun parseTableInfo(bytes: ByteArray): SmartChassisTableInfo? {
        if (bytes.size != SmartChassisProtocol.TABLE_INFO_SIZE) {
            return null
        }
        return SmartChassisTableInfo(
            tableSeq = bytes.u32Le(0),
            crc16 = bytes.u16Le(4),
            slotCount = bytes.u8(6)
        )
    }

    fun encodeLightCommand(command: SmartChassisLightCommand): ByteArray {
        require(command.mode != SmartChassisLightMode.UNKNOWN) { "mode must be a known light mode" }
        require(command.maskA in 0..SmartChassisProtocol.SLOT_MASK_MAX) { "maskA only supports 25 slots" }
        require(command.maskB in 0..SmartChassisProtocol.SLOT_MASK_MAX) { "maskB only supports 25 slots" }
        require(command.timeoutSeconds in 0..SmartChassisProtocol.MAX_LIGHT_TIMEOUT_SECONDS) {
            "timeoutSeconds must be 0..300"
        }
        require(command.mode != SmartChassisLightMode.FX ||
            command.timeoutSeconds in 0..SmartChassisProtocol.MAX_FX_TIMEOUT_SECONDS) {
            "FX timeoutSeconds must be 0..10"
        }
        return ByteArray(SmartChassisProtocol.LIGHT_COMMAND_SIZE).also { bytes ->
            bytes[0] = command.mode.code.toByte()
            bytes.putU32Le(1, command.maskA.toLong())
            bytes.putU32Le(5, command.maskB.toLong())
            bytes.putRgb(9, command.colorA)
            bytes.putRgb(12, command.colorB)
            bytes.putU16Le(15, command.timeoutSeconds)
        }
    }

    fun parseLightStatus(bytes: ByteArray): SmartChassisLightStatus? {
        if (bytes.size != 3) {
            return null
        }
        val rawMode = bytes.u8(0)
        return SmartChassisLightStatus(
            mode = SmartChassisLightMode.fromCode(rawMode),
            rawMode = rawMode,
            remainingSeconds = bytes.u16Le(1)
        )
    }

    fun slotMask(slot: Int): Int {
        return if (slot in 1..SmartChassisProtocol.SLOT_COUNT) {
            1 shl (slot - 1)
        } else {
            0
        }
    }

    fun crc8Maxim(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        var crc = 0x00
        for (index in offset until offset + length) {
            crc = crc xor bytes.u8(index)
            repeat(8) {
                crc = if ((crc and 0x01) != 0) {
                    (crc ushr 1) xor 0x8C
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFF
    }

    fun crc16CcittFalse(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        var crc = 0xFFFF
        for (index in offset until offset + length) {
            crc = crc xor (bytes.u8(index) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    private fun ByteArray.decodePartId(): String {
        val length = (1 until 11).firstOrNull { index -> this[index].toInt() == 0 }?.minus(1) ?: 10
        return String(this, 1, length, StandardCharsets.US_ASCII).trim()
    }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

    private fun ByteArray.u16Le(offset: Int): Int {
        return u8(offset) or (u8(offset + 1) shl 8)
    }

    private fun ByteArray.u32Le(offset: Int): Long {
        return u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)
    }

    private fun ByteArray.putU16Le(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun ByteArray.putU32Le(offset: Int, value: Long) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun ByteArray.putRgb(offset: Int, color: RgbColor) {
        require(color.red in 0..0xFF && color.green in 0..0xFF && color.blue in 0..0xFF) {
            "RGB channels must fit uint8"
        }
        this[offset] = color.red.toByte()
        this[offset + 1] = color.green.toByte()
        this[offset + 2] = color.blue.toByte()
    }
}
