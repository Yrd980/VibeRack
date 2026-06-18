package com.viberack.app.core.ble.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.util.Locale

class BoxLayerLabel(positionCode: String, partNumber: String) {
    val positionCode: String = positionCode.trim()
    val partNumber: String = partNumber.trim().uppercase(Locale.ROOT)

    override fun equals(other: Any?): Boolean {
        return other is BoxLayerLabel &&
            positionCode == other.positionCode &&
            partNumber == other.partNumber
    }

    override fun hashCode(): Int = 31 * positionCode.hashCode() + partNumber.hashCode()

    override fun toString(): String = "BoxLayerLabel(positionCode=$positionCode, partNumber=$partNumber)"
}

data class P0PrintChunk(
    val label: String,
    val bytes: ByteArray,
    val delayAfterMilliseconds: Long
) {
    override fun equals(other: Any?): Boolean {
        return other is P0PrintChunk &&
            label == other.label &&
            bytes.contentEquals(other.bytes) &&
            delayAfterMilliseconds == other.delayAfterMilliseconds
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + delayAfterMilliseconds.hashCode()
        return result
    }
}

object BoxLayerLabelRenderer {
    const val targetWidthDots = 384
    const val targetHeightDots = 232

    fun renderPreview(label: BoxLayerLabel): Bitmap {
        require(label.positionCode.isNotEmpty()) { "positionCode is required" }
        require(label.partNumber.isNotEmpty()) { "partNumber is required" }
        return bitmap().also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawCentered(label.positionCode, 4f, 0f, 376f, 112f, 96f, Typeface.DEFAULT_BOLD)
                drawCentered(label.partNumber, 4f, 104f, 376f, 124f, 94f, Typeface.MONOSPACE)
            }
        }
    }

    fun renderP0PrintImage(label: BoxLayerLabel): Bitmap {
        require(label.positionCode.isNotEmpty()) { "positionCode is required" }
        require(label.partNumber.isNotEmpty()) { "partNumber is required" }
        return bitmap().also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                save()
                translate(8f, targetHeightDots - 10f)
                rotate(-90f)
                drawFittedLine(label.positionCode, 68f, targetHeightDots - 12f, 58f, 34f, Typeface.DEFAULT_BOLD)
                drawFittedLine(label.partNumber, 130f, targetHeightDots - 12f, 54f, 32f, Typeface.MONOSPACE)
                restore()
            }
        }
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(targetWidthDots, targetHeightDots, Bitmap.Config.ARGB_8888)

    private fun Canvas.drawCentered(
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        textSize: Float,
        typeface: Typeface
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        fitText(paint, text, width, 24f)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        drawText(text, x + width / 2f, y + height / 2f - bounds.exactCenterY(), paint)
    }

    private fun Canvas.drawFittedLine(
        text: String,
        baseline: Float,
        maxWidth: Float,
        textSize: Float,
        minTextSize: Float,
        typeface: Typeface
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            this.typeface = typeface
        }
        fitText(paint, text, maxWidth, minTextSize)
        drawText(ellipsizeEnd(text, paint, maxWidth), 0f, baseline, paint)
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float, minTextSize: Float) {
        while (paint.textSize > minTextSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= 1f
        }
    }

    private fun ellipsizeEnd(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0) {
            val candidate = text.take(end) + "..."
            if (paint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return "..."
    }
}

object P0BitmapProtocol {
    const val targetWidthDots = 384
    const val targetHeightDots = 232
    const val widthBytes = targetWidthDots / 8

    private const val commandPrefix = 0x1F
    private const val commandPageStart = 0x20
    private const val commandPageWidth = 0x27
    private const val commandBitmapPrint = 0x2B
    private const val commandBitmapRepeat = 0x2E
    private const val commandGapType = 0x42
    private const val commandDarkness = 0x43
    private const val commandSpeed = 0x44
    private const val fixedCrc = 0x88

    fun buildBitmapPrintChunks(image: Bitmap): List<P0PrintChunk> = buildPrintChunks(rasterize(image))

    fun buildPrintChunks(rows: List<ByteArray>): List<P0PrintChunk> {
        val chunks = mutableListOf(
            P0PrintChunk(
                label = "p0 page start",
                bytes = buildCommand(commandPageStart, shortBytes(1)) +
                    buildCommand(commandPageWidth, ebv(widthBytes)) +
                    buildCommand(commandGapType, byteArrayOf(2)) +
                    buildCommand(commandDarkness, byteArrayOf(5)) +
                    buildCommand(commandSpeed, byteArrayOf(2)),
                delayAfterMilliseconds = 80
            )
        )

        var blankRows = 0
        for (row in rows) {
            val activeLength = row.indexOfLast { it.toInt() != 0 } + 1
            if (activeLength > 0) {
                if (blankRows > 0) {
                    appendBlankRows(blankRows, chunks)
                    blankRows = 0
                }
                chunks += P0PrintChunk(
                    label = "p0 bitmap row",
                    bytes = byteArrayOf(commandPrefix.toByte(), commandBitmapPrint.toByte()) +
                        ebv(0) +
                        ebv(activeLength) +
                        row.copyOf(activeLength),
                    delayAfterMilliseconds = 2
                )
            } else {
                blankRows++
            }
        }
        if (blankRows > 0) appendBlankRows(blankRows, chunks)
        chunks += P0PrintChunk("p0 page print", byteArrayOf(0x0C), 120)
        return chunks
    }

    private fun rasterize(image: Bitmap): List<ByteArray> {
        val scaled = if (image.width == targetWidthDots && image.height == targetHeightDots) {
            image
        } else {
            Bitmap.createScaledBitmap(image, targetWidthDots, targetHeightDots, true)
        }
        return (0 until targetHeightDots).map { y ->
            ByteArray(widthBytes).also { row ->
                for (x in 0 until targetWidthDots) {
                    val pixel = scaled.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                    if (alpha >= 128 && luminance < 150) {
                        row[x / 8] = (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                    }
                }
            }
        }
    }

    private fun appendBlankRows(count: Int, chunks: MutableList<P0PrintChunk>) {
        var remaining = count
        while (remaining > 0) {
            val rows = minOf(remaining, 16_384)
            chunks += if (rows <= 255) {
                P0PrintChunk("p0 blank rows", byteArrayOf(0x1B, 0x4A, rows.toByte()), 1)
            } else {
                P0PrintChunk("p0 repeated blank rows", buildCommand(commandBitmapRepeat, ebv(rows - 1)), 1)
            }
            remaining -= rows
        }
    }

    private fun buildCommand(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val size = if (payload.size >= 192) {
            byteArrayOf(((payload.size shr 8) or 0xC0).toByte(), payload.size.toByte())
        } else {
            byteArrayOf(payload.size.toByte())
        }
        return byteArrayOf(commandPrefix.toByte(), command.toByte()) + size + payload + fixedCrc.toByte()
    }

    private fun shortBytes(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun ebv(value: Int): ByteArray {
        return if (value >= 192) {
            byteArrayOf(((value shr 8) or 0xC0).toByte(), value.toByte())
        } else {
            byteArrayOf(value.toByte())
        }
    }
}
