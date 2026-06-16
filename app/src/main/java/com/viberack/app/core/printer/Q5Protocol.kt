package com.viberack.app.core.printer

import java.nio.charset.StandardCharsets
import java.util.UUID

data class ParsedFrame(
    val command: Int,
    val flag1: Int,
    val flag2: Int,
    val payload: ByteArray,
)

data class Q5DeviceInfo(
    val name: String,
    val mac: String,
    val standbyMinutes: Int?,
    val batteryPercent: Int?,
    val firmwareVersion: String,
    val serialNumber: String,
)

data class Q5TxChunk(
    val label: String,
    val bytes: ByteArray,
    val delayAfterMs: Long = 0,
)

object Q5Protocol {
    val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    val queryDeviceInfo: ByteArray = byteArrayOf(
        0x02,
        0x34,
        0x00,
        0x00,
        0x01,
        0x00,
        0x40,
        0xBF.toByte(),
        0x03,
    )

    val startImagePrint: ByteArray = buildCommandWithPayload(0x1A, byteArrayOf(0x01))
    val finishImageTransfer: ByteArray = buildCommandWithPayload(0x2A, byteArrayOf(0x00))
    val finalizePrint: ByteArray = buildCommandWithPayload(0x33, byteArrayOf(0x00))
    val printCommandAscii: ByteArray = "PRINT 1,1\r\n".toByteArray(StandardCharsets.US_ASCII)

    fun buildCommandWithPayload(
        command: Int,
        payload: ByteArray,
        flag1: Int = 0,
        flag2: Int = 0,
    ): ByteArray {
        require(payload.size <= 0xFF) { "payload too large for simple frame" }
        val crc = crc16Modbus(payload)
        return byteArrayOf(
            0x02,
            command.toByte(),
            flag1.toByte(),
            flag2.toByte(),
            payload.size.toByte(),
        ) + payload + byteArrayOf(
            (crc shr 8).toByte(),
            crc.toByte(),
            0x03,
        )
    }

    fun parseDeviceInfo(frame: ParsedFrame): Q5DeviceInfo? {
        if (frame.command != 0x35) return null
        val text = decodeAscii(frame.payload)
        val parts = text.split('|')
        if (parts.size < 6) return null
        return Q5DeviceInfo(
            name = parts[0],
            mac = parts[1],
            standbyMinutes = parts[2].toIntOrNull(),
            batteryPercent = parts[3].toIntOrNull(),
            firmwareVersion = parts[4],
            serialNumber = parts[5],
        )
    }

    fun isAck(frame: ParsedFrame): Boolean {
        return frame.payload.size == 1 && frame.payload[0] == 0x03.toByte()
    }

    fun tryExtractFrames(buffer: ByteArray): Pair<List<ParsedFrame>, ByteArray> {
        val frames = mutableListOf<ParsedFrame>()
        var index = 0
        while (index < buffer.size) {
            if (buffer[index] != 0x02.toByte()) {
                index++
                continue
            }
            if (buffer.size - index < 8) {
                break
            }
            val payloadLength = buffer[index + 4].toInt() and 0xFF
            val frameLength = payloadLength + 8
            if (buffer.size - index < frameLength) {
                break
            }
            val endIndex = index + frameLength - 1
            if (buffer[endIndex] != 0x03.toByte()) {
                index++
                continue
            }
            val payloadStart = index + 5
            val payloadEnd = payloadStart + payloadLength
            frames += ParsedFrame(
                command = buffer[index + 1].toInt() and 0xFF,
                flag1 = buffer[index + 2].toInt() and 0xFF,
                flag2 = buffer[index + 3].toInt() and 0xFF,
                payload = buffer.copyOfRange(payloadStart, payloadEnd),
            )
            index += frameLength
        }
        return frames to buffer.copyOfRange(index, buffer.size)
    }

    private fun decodeAscii(bytes: ByteArray): String {
        return bytes.toString(StandardCharsets.US_ASCII).removePrefix("\u0000")
    }

    private fun crc16Modbus(payload: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in payload) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }
}
