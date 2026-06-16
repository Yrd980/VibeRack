package com.viberack.app.core.printer

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.min

object Q5ImageEncoder {
    private const val targetWidthDots = 384
    private const val targetHeightDots = 232
    private const val widthBytes = targetWidthDots / 8
    private const val rowsPerBand = 33
    private val bandPrefix80Ca = byteArrayOf(0x80.toByte(), 0xCA.toByte())
    private val imageFrameTail = byteArrayOf(0x00, 0x1D, 0x03)

    private data class EncodedBand(
        val payloadCompressed: ByteArray,
        val footerContinuation: ByteArray,
    )

    private data class EncodedFrame(
        val bytes: ByteArray,
        val description: String,
    )

    fun buildBitmapPrintChunks(bitmap: Bitmap): List<Q5TxChunk> {
        val rasterRows = rasterize(bitmap)
        val imageFrames = encodeBands(rasterRows)
        return buildList {
            add(
                Q5TxChunk(
                    label = "q5 print preamble",
                    bytes = Q5Protocol.startImagePrint + "CLS\r\n".toByteArray(Charsets.US_ASCII),
                    delayAfterMs = 120,
                )
            )
            imageFrames.forEachIndexed { index, frame ->
                add(
                    Q5TxChunk(
                        label = "q5 image frame ${index + 1} ${frame.description}",
                        bytes = frame.bytes,
                        delayAfterMs = 30,
                    )
                )
            }
            add(
                Q5TxChunk(
                    label = "q5 print tail",
                    bytes = Q5Protocol.finishImageTransfer +
                        Q5Protocol.printCommandAscii +
                        Q5Protocol.finalizePrint,
                    delayAfterMs = 120,
                )
            )
        }
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
                val isBlack = alpha >= 128 && luminance < 180
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
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            targetWidthDots,
            targetHeightDots,
            true,
        )
        val pixels = IntArray(targetWidthDots * targetHeightDots)
        scaled.getPixels(pixels, 0, targetWidthDots, 0, 0, targetWidthDots, targetHeightDots)

        val binaryPixels = IntArray(pixels.size)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val alpha = color ushr 24 and 0xFF
            val red = color ushr 16 and 0xFF
            val green = color ushr 8 and 0xFF
            val blue = color and 0xFF
            val luminance = (red * 299 + green * 587 + blue * 114) / 1000
            val isBlack = alpha >= 128 && luminance < 180
            binaryPixels[index] = if (isBlack) {
                0xFF000000.toInt()
            } else {
                0xFFFFFFFF.toInt()
            }
        }

        return Bitmap.createBitmap(targetWidthDots, targetHeightDots, Bitmap.Config.ARGB_8888).apply {
            setPixels(binaryPixels, 0, targetWidthDots, 0, 0, targetWidthDots, targetHeightDots)
        }
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

    private fun encodeBands(rows: List<ByteArray>): List<EncodedFrame> {
        require(rows.isNotEmpty()) { "rows are empty" }
        require(rows.all { it.size == widthBytes }) { "invalid row width" }

        val activeRows = rows.withIndex()
            .filter { (_, row) -> row.any { it.toInt() != 0 } }
            .map { it.index }
        if (activeRows.isEmpty()) {
            return emptyList()
        }

        val firstBandOffset = (activeRows.first() / rowsPerBand) * rowsPerBand
        val lastBandExclusive = min(
            rows.size,
            ((activeRows.last() / rowsPerBand) + 1) * rowsPerBand,
        )
        val frames = mutableListOf<EncodedFrame>()
        var rowOffset = firstBandOffset
        while (rowOffset < lastBandExclusive) {
            val bandRows = rows.subList(rowOffset, min(rowOffset + rowsPerBand, lastBandExclusive))
            val encodedBand = encodeBand80Ca(bandRows)
            val payload = buildBandPayload(
                rowOffset = rowOffset,
                rowsInBand = bandRows.size,
                compressedRows = encodedBand.payloadCompressed,
            )
            frames += EncodedFrame(
                bytes = buildImageFrame(payload, encodedBand.footerContinuation),
                description = "offset=$rowOffset rows=${bandRows.size}",
            )
            rowOffset += bandRows.size
        }
        return frames
    }

    private fun buildImageFrame(
        payload: ByteArray,
        footerContinuation: ByteArray,
    ): ByteArray {
        require(payload.size <= 0xFFFF) { "image payload too large" }
        require(footerContinuation.size == 2) { "80ca footer continuation must be 2 bytes" }
        return byteArrayOf(
            0x02,
            0x28,
            0x00,
            (payload.size shr 8).toByte(),
            payload.size.toByte(),
        ) + payload + footerContinuation + imageFrameTail
    }

    private fun buildBandPayload(
        rowOffset: Int,
        rowsInBand: Int,
        compressedRows: ByteArray,
    ): ByteArray {
        val uncompressedLength = widthBytes * rowsInBand
        return byteArrayOf(
            0x00,
            0x00,
            (rowOffset and 0xFF).toByte(),
            ((rowOffset shr 8) and 0xFF).toByte(),
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (rowsInBand and 0xFF).toByte(),
            ((rowsInBand shr 8) and 0xFF).toByte(),
            (uncompressedLength shr 24).toByte(),
            (uncompressedLength shr 16).toByte(),
            (uncompressedLength shr 8).toByte(),
            uncompressedLength.toByte(),
            (compressedRows.size shr 24).toByte(),
            (compressedRows.size shr 16).toByte(),
            (compressedRows.size shr 8).toByte(),
            compressedRows.size.toByte(),
        ) + compressedRows
    }

    private fun encodeBand80Ca(rows: List<ByteArray>): EncodedBand {
        val flat = ByteArray(rows.size * widthBytes)
        var destOffset = 0
        rows.forEach { row ->
            row.copyInto(flat, destinationOffset = destOffset)
            destOffset += row.size
        }

        val trailingValue = flat.last()
        var trailingRun = 1
        val maxTrailingRun = min(127, flat.size)
        while (trailingRun < maxTrailingRun && flat[flat.size - trailingRun - 1] == trailingValue) {
            trailingRun++
        }

        val payloadCompressed = ArrayList<Byte>()
        bandPrefix80Ca.forEach(payloadCompressed::add)
        encode80CaRange(flat, 0, flat.size - trailingRun).forEach(payloadCompressed::add)

        return EncodedBand(
            payloadCompressed = payloadCompressed.toByteArray(),
            footerContinuation = byteArrayOf((0x80 + trailingRun).toByte(), trailingValue),
        )
    }

    private fun encode80CaRange(
        data: ByteArray,
        start: Int,
        endExclusive: Int,
    ): ByteArray {
        val out = ArrayList<Byte>()
        val literal = ArrayList<Byte>()

        fun flushLiteral() {
            if (literal.isEmpty()) return
            out += literal.size.toByte()
            out.addAll(literal)
            literal.clear()
        }

        var index = start
        while (index < endExclusive) {
            val value = data[index]
            var runLength = 1
            while (
                index + runLength < endExclusive &&
                data[index + runLength] == value &&
                runLength < 127
            ) {
                runLength++
            }
            if (runLength >= 3) {
                flushLiteral()
                out += (0x80 + runLength).toByte()
                out += value
                index += runLength
            } else {
                literal += value
                if (literal.size == 127) {
                    flushLiteral()
                }
                index++
            }
        }
        flushLiteral()
        return out.toByteArray()
    }
}
