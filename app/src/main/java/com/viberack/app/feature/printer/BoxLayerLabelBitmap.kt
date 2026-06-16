package com.viberack.app.feature.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import com.viberack.app.core.printer.PrintTypeface

data class BoxLayerLabelContent(
    val positionCode: String,
    val partNumber: String,
    val note: String? = null,
)

object BoxLayerLabelBitmap {
    const val widthDots = 384
    const val heightDots = 232

    fun create10MmBitmap(
        positionCode: String,
        partNumber: String,
        note: String? = null,
    ): Bitmap {
        return create10MmBitmap(
            BoxLayerLabelContent(
                positionCode = positionCode,
                partNumber = partNumber,
                note = note,
            )
        )
    }

    fun create10MmBitmap(content: BoxLayerLabelContent): Bitmap {
        val bitmap = Bitmap.createBitmap(widthDots, heightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val positionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 36f
            typeface = PrintTypeface.bold
        }
        val partPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 32f
            typeface = PrintTypeface.bold
        }
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 24f
            typeface = PrintTypeface.regular
        }

        canvas.save()
        canvas.translate(8f, heightDots - 10f)
        canvas.rotate(-90f)
        drawFittedLine(
            canvas = canvas,
            text = content.positionCode,
            paint = positionPaint,
            baseline = 36f,
            minTextSize = 26f,
        )
        content.partNumber.takeIf { it.isNotBlank() }?.let { partNumber ->
            drawFittedLine(
                canvas = canvas,
                text = partNumber,
                paint = partPaint,
                baseline = 72f,
                minTextSize = 24f,
            )
        }
        content.note?.takeIf { it.isNotBlank() }?.let { note ->
            drawFittedLine(
                canvas = canvas,
                text = note,
                paint = notePaint,
                baseline = 104f,
                minTextSize = 18f,
            )
        }
        canvas.restore()

        return bitmap
    }

    private fun drawFittedLine(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        baseline: Float,
        minTextSize: Float,
    ) {
        val originalTextSize = paint.textSize
        val trimmedText = text.trim()
        var textSize = originalTextSize
        while (textSize > minTextSize && paint.measureText(trimmedText) > maxRotatedTextWidth) {
            textSize -= 1f
            paint.textSize = textSize
        }

        val fittedText = ellipsizeEnd(trimmedText, paint, maxRotatedTextWidth)
        canvas.drawText(fittedText, 0f, baseline, paint)
        paint.textSize = originalTextSize
    }

    private fun ellipsizeEnd(
        text: String,
        paint: TextPaint,
        maxWidth: Float,
    ): String {
        if (paint.measureText(text) <= maxWidth) {
            return text
        }

        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        var endIndex = text.length
        while (endIndex > 0 && paint.measureText(text, 0, endIndex) + ellipsisWidth > maxWidth) {
            endIndex--
        }
        return if (endIndex <= 0) ellipsis else text.substring(0, endIndex) + ellipsis
    }

    private const val maxRotatedTextWidth = heightDots - 18f
}
