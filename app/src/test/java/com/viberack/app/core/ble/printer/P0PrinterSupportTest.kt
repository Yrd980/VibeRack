package com.viberack.app.core.ble.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P0PrinterSupportTest {
    @Test
    fun normalizesBoxLayerLabelInput() {
        val label = BoxLayerLabel(" A-01 ", " c2040 ")

        assertEquals("A-01", label.positionCode)
        assertEquals("C2040", label.partNumber)
        assertEquals(384, BoxLayerLabelRenderer.targetWidthDots)
        assertEquals(232, BoxLayerLabelRenderer.targetHeightDots)
    }

    @Test
    fun buildsP0BitmapCommandsWithExpectedBounds() {
        val rows = List(P0BitmapProtocol.targetHeightDots) { y ->
            ByteArray(P0BitmapProtocol.widthBytes).also {
                if (y == 4) it[0] = 0x80.toByte()
            }
        }

        val chunks = P0BitmapProtocol.buildPrintChunks(rows)

        assertArrayEquals(byteArrayOf(0x1F, 0x20, 0x02, 0x00), chunks.first().bytes.copyOf(4))
        assertArrayEquals(byteArrayOf(0x0C), chunks.last().bytes)
        assertTrue(chunks.any { it.bytes.take(2) == listOf(0x1F.toByte(), 0x2B.toByte()) })
    }

    @Test
    fun exposesIosCompatibleP0BleUuids() {
        assertEquals("000018f0-0000-1000-8000-00805f9b34fb", P0BlePrinterUuids.advertisedService.toString())
        assertEquals("49535343-fe7d-4ae5-8fa9-9fafd205e455", P0BlePrinterUuids.printService.toString())
        assertEquals("49535343-1e4d-4bd9-ba61-23c647249616", P0BlePrinterUuids.notifyCharacteristic.toString())
        assertEquals("49535343-8841-43f4-a8d4-ecbe34729bb3", P0BlePrinterUuids.writeCharacteristic.toString())
    }
}
