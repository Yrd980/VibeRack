package com.viberack.app.core.ble.smart

internal object SmartChassisReadAllValidator {
    fun validate(
        records: List<SmartChassisSlotRecord>,
        tableInfo: SmartChassisTableInfo
    ): String? {
        if (tableInfo.slotCount != SmartChassisProtocol.SLOT_COUNT) {
            return "Table Info slot_count ${tableInfo.slotCount} does not match ${SmartChassisProtocol.SLOT_COUNT}"
        }
        if (records.size != tableInfo.slotCount) {
            return "READ_ALL returned ${records.size} records, table reports ${tableInfo.slotCount}"
        }
        records.forEachIndexed { index, record ->
            if (record.slot != 0 && record.slot != index + 1) {
                return "READ_ALL record ${index + 1} contains slot ${record.slot}"
            }
        }
        val tableBytes = records
            .flatMap { record ->
                SmartChassisCodec.encodeSlotRecordForTable(record).asIterable()
            }
            .toByteArray()
        val crc16 = SmartChassisCodec.crc16CcittFalse(tableBytes)
        return if (crc16 == tableInfo.crc16) {
            null
        } else {
            "READ_ALL CRC $crc16 does not match table CRC ${tableInfo.crc16}"
        }
    }
}
