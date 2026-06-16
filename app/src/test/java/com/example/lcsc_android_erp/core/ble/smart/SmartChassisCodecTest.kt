package com.example.lcsc_android_erp.core.ble.smart

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartChassisCodecTest {
    @Test
    fun parsesCoreAdvertisementAndIgnoresReservedTail() {
        val advertisement = SmartChassisCodec.parseAdvertisement(
            byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(),
                0x01,
                0xE9.toByte(),
                0x03,
                88,
                SmartChassisProtocol.ADV_LIGHT_ACTIVE.toByte(),
                0x34,
                0x12,
                0x00,
                0x00
            )
        )

        assertNotNull(advertisement)
        assertEquals(SmartChassisProtocol.DEV_COMPANY_ID, advertisement!!.companyId)
        assertEquals(1, advertisement.protoVersion)
        assertEquals(1001, advertisement.batchId)
        assertEquals(88, advertisement.batteryPct)
        assertEquals(SmartChassisProtocol.ADV_LIGHT_ACTIVE, advertisement.statusFlags)
        assertEquals(0x1234, advertisement.tableSeqLow16)
    }

    @Test
    fun parsesAndroidManufacturerPayloadWithoutCompanyBytes() {
        val advertisement = SmartChassisCodec.parseAndroidManufacturerPayload(
            companyId = SmartChassisProtocol.DEV_COMPANY_ID,
            payload = byteArrayOf(0x01, 0xD2.toByte(), 0x04, 97, 0x02, 0x78, 0x56)
        )

        assertNotNull(advertisement)
        assertEquals(1234, advertisement!!.batchId)
        assertEquals(97, advertisement.batteryPct)
        assertEquals(0x5678, advertisement.tableSeqLow16)
    }

    @Test
    fun encodesAndParsesSlotRecordWithCrc() {
        val encoded = SmartChassisCodec.encodeSlotRecord(
            slot = 7,
            partId = "c12345",
            quantity = 330,
            flags = SmartChassisProtocol.SLOT_FLAG_LOW_STOCK
        )

        assertEquals(SmartChassisProtocol.SLOT_RECORD_SIZE, encoded.size)
        assertEquals(7, encoded[0].toInt())
        assertEquals(
            SmartChassisCodec.crc8Maxim(encoded, 0, SmartChassisProtocol.SLOT_RECORD_SIZE - 1),
            encoded[15].toInt() and 0xFF
        )

        val parsed = SmartChassisCodec.parseSlotRecord(encoded)
        assertNotNull(parsed)
        assertEquals(7, parsed!!.slot)
        assertEquals("C12345", parsed.partId)
        assertEquals(330, parsed.quantity)
        assertEquals(SmartChassisProtocol.SLOT_FLAG_LOW_STOCK, parsed.flags)

        encoded[11] = 0
        assertNull(SmartChassisCodec.parseSlotRecord(encoded))
    }

    @Test
    fun crcImplementationsMatchKnownCheckValues() {
        val bytes = "123456789".encodeToByteArray()

        assertEquals(0xA1, SmartChassisCodec.crc8Maxim(bytes))
        assertEquals(0x29B1, SmartChassisCodec.crc16CcittFalse(bytes))
    }

    @Test
    fun parsesTableInfo() {
        val tableInfo = SmartChassisCodec.parseTableInfo(
            byteArrayOf(0x78, 0x56, 0x34, 0x12, 0xCD.toByte(), 0xAB.toByte(), 25)
        )

        assertNotNull(tableInfo)
        assertEquals(0x12345678L, tableInfo!!.tableSeq)
        assertEquals(0xABCD, tableInfo.crc16)
        assertEquals(25, tableInfo.slotCount)
        assertNull(SmartChassisCodec.parseTableInfo(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun encodesSeventeenByteLightCommand() {
        val command = SmartChassisLightCommand(
            mode = SmartChassisLightMode.SORT,
            maskA = SmartChassisCodec.slotMask(1) or SmartChassisCodec.slotMask(25),
            maskB = SmartChassisCodec.slotMask(7),
            colorA = RgbColor(255, 0, 16),
            colorB = RgbColor(0, 128, 64),
            timeoutSeconds = 120
        )

        val encoded = SmartChassisCodec.encodeLightCommand(command)

        assertEquals(17, encoded.size)
        assertEquals(SmartChassisLightMode.SORT.code, encoded[0].toInt() and 0xFF)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00, 0x01), encoded.copyOfRange(1, 5))
        assertArrayEquals(byteArrayOf(0x40, 0x00, 0x00, 0x00), encoded.copyOfRange(5, 9))
        assertArrayEquals(byteArrayOf(255.toByte(), 0, 16), encoded.copyOfRange(9, 12))
        assertArrayEquals(byteArrayOf(0, 128.toByte(), 64), encoded.copyOfRange(12, 15))
        assertArrayEquals(byteArrayOf(120, 0), encoded.copyOfRange(15, 17))
    }

    @Test
    fun recognizesReadAllEndPayload() {
        assertTrue(SmartChassisCodec.isReadAllEndPayload(byteArrayOf(0xFF.toByte())))
        assertFalse(SmartChassisCodec.isReadAllEndPayload(byteArrayOf(0xFF.toByte(), 0x00)))
    }
}
