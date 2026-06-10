package com.example.lcsc_android_erp.core.printer

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.UUID
import kotlin.math.min

data class P0TxChunk(
    val label: String,
    val bytes: ByteArray,
    val delayAfterMs: Long = 0,
)

object P0Protocol {
    val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private const val commandPrefix = 0x1F
    private const val commandPageStart = 0x20
    private const val commandPageWidth = 0x27
    private const val commandBitmapPrint = 0x2B
    private const val commandBitmapRepeat = 0x2E
    private const val commandGapType = 0x42
    private const val commandDarkness = 0x43
    private const val commandSpeed = 0x44
    private const val fixedCrc = 0x88
    private const val targetWidthDots = 384
    private const val targetHeightDots = 232
    private const val widthBytes = targetWidthDots / 8

    fun buildBitmapPrintChunks(bitmap: Bitmap): List<P0TxChunk> {
        val rows = rasterize(bitmap)
        return buildList {
            add(
                P0TxChunk(
                    label = "p0 page start",
                    bytes = concat(
                        buildCommand(commandPageStart, shortBytes(1)),
                        buildCommand(commandPageWidth, ebv(widthBytes)),
                        buildCommand(commandGapType, byteArrayOf(2)),
                        buildCommand(commandDarkness, byteArrayOf(5)),
                        buildCommand(commandSpeed, byteArrayOf(2)),
                    ),
                    delayAfterMs = 80,
                )
            )

            var pendingBlankRows = 0
            rows.forEach { row ->
                val lastBlackByteIndex = row.indexOfLast { it.toInt() != 0 }
                if (lastBlackByteIndex < 0) {
                    pendingBlankRows++
                } else {
                    if (pendingBlankRows > 0) {
                        addBlankRows(pendingBlankRows)
                        pendingBlankRows = 0
                    }
                    val activeLength = lastBlackByteIndex + 1
                    add(
                        P0TxChunk(
                            label = "p0 bitmap row",
                            bytes = buildBitmapRow(row, activeLength),
                            delayAfterMs = 2,
                        )
                    )
                }
            }
            if (pendingBlankRows > 0) {
                addBlankRows(pendingBlankRows)
            }
            add(
                P0TxChunk(
                    label = "p0 page print",
                    bytes = byteArrayOf(0x0C),
                    delayAfterMs = 120,
                )
            )
        }
    }

    private fun MutableList<P0TxChunk>.addBlankRows(count: Int) {
        var remaining = count
        while (remaining > 0) {
            val rows = min(remaining, 16_384)
            if (rows <= 255) {
                add(
                    P0TxChunk(
                        label = "p0 blank rows",
                        bytes = byteArrayOf(0x1B, 0x4A, rows.toByte()),
                        delayAfterMs = 1,
                    )
                )
            } else {
                add(
                    P0TxChunk(
                        label = "p0 repeated blank rows",
                        bytes = buildCommand(commandBitmapRepeat, ebv(rows - 1)),
                        delayAfterMs = 1,
                    )
                )
            }
            remaining -= rows
        }
    }

    private fun buildBitmapRow(row: ByteArray, activeLength: Int): ByteArray {
        val payloadPrefix = ByteArray(4)
        var offset = pushEbv(payloadPrefix, 0, 0)
        offset = pushEbv(payloadPrefix, offset, activeLength)
        return byteArrayOf(
            commandPrefix.toByte(),
            commandBitmapPrint.toByte(),
        ) + payloadPrefix.copyOf(offset) + row.copyOf(activeLength)
    }

    private fun buildCommand(command: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        return if (payload.size >= 192) {
            byteArrayOf(
                commandPrefix.toByte(),
                command.toByte(),
                ((payload.size ushr 8) or 0xC0).toByte(),
                payload.size.toByte(),
            ) + payload + byteArrayOf(fixedCrc.toByte())
        } else {
            byteArrayOf(
                commandPrefix.toByte(),
                command.toByte(),
                payload.size.toByte(),
            ) + payload + byteArrayOf(fixedCrc.toByte())
        }
    }

    private fun shortBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
    }

    private fun ebv(value: Int): ByteArray {
        return if (value >= 192) {
            byteArrayOf(((value ushr 8) or 0xC0).toByte(), value.toByte())
        } else {
            byteArrayOf(value.toByte())
        }
    }

    private fun pushEbv(buffer: ByteArray, offset: Int, value: Int): Int {
        return if (value >= 192) {
            buffer[offset] = ((value ushr 8) or 0xC0).toByte()
            buffer[offset + 1] = value.toByte()
            offset + 2
        } else {
            buffer[offset] = value.toByte()
            offset + 1
        }
    }

    private fun concat(vararg byteArrays: ByteArray): ByteArray {
        val output = ByteArray(byteArrays.sumOf { it.size })
        var offset = 0
        byteArrays.forEach { bytes ->
            bytes.copyInto(output, destinationOffset = offset)
            offset += bytes.size
        }
        return output
    }

    private fun rasterize(source: Bitmap): List<ByteArray> {
        val binaryBitmap = normalizeToBinaryBitmap(source)
        val pixels = IntArray(targetWidthDots * targetHeightDots)
        binaryBitmap.getPixels(pixels, 0, targetWidthDots, 0, 0, targetWidthDots, targetHeightDots)

        return List(targetHeightDots) { y ->
            val row = ByteArray(widthBytes)
            for (x in 0 until targetWidthDots) {
                val color = pixels[y * targetWidthDots + x]
                val alpha = color ushr 24 and 0xFF
                val red = color ushr 16 and 0xFF
                val green = color ushr 8 and 0xFF
                val blue = color and 0xFF
                val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                val isBlack = alpha >= 128 && luminance < 150
                if (isBlack) {
                    row[x / 8] = (row[x / 8].toInt() or (1 shl (7 - (x % 8)))).toByte()
                }
            }
            row
        }
    }

    private fun normalizeToBinaryBitmap(source: Bitmap): Bitmap {
        val cropRect = centerCropToAspectRatio(
            width = source.width,
            height = source.height,
            targetAspect = targetWidthDots.toFloat() / targetHeightDots.toFloat(),
        )
        val cropped = Bitmap.createBitmap(
            source,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height(),
        )
        return Bitmap.createScaledBitmap(
            cropped,
            targetWidthDots,
            targetHeightDots,
            true,
        )
    }

    private fun centerCropToAspectRatio(
        width: Int,
        height: Int,
        targetAspect: Float,
    ): Rect {
        val sourceAspect = width.toFloat() / height.toFloat()
        return if (sourceAspect > targetAspect) {
            val croppedWidth = (height * targetAspect).toInt()
            val left = (width - croppedWidth) / 2
            Rect(left, 0, left + croppedWidth, height)
        } else {
            val croppedHeight = (width / targetAspect).toInt()
            val top = (height - croppedHeight) / 2
            Rect(0, top, width, top + croppedHeight)
        }
    }
}
