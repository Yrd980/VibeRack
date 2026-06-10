package com.example.lcsc_android_erp.feature.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import com.example.lcsc_android_erp.core.printer.PrintTypeface

object PrinterSmokeTestLabel {
    fun createBitmap(): Bitmap {
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
        val testPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 24f
            typeface = PrintTypeface.regular
        }

        canvas.save()
        canvas.translate(8f, heightDots - 10f)
        canvas.rotate(-90f)
        drawLine(canvas, "BOX01-L01", positionPaint, baseline = 36f)
        drawLine(canvas, "C17710", partPaint, baseline = 72f)
        drawLine(canvas, "TEST PRINT", testPaint, baseline = 104f)
        canvas.restore()

        return bitmap
    }

    private fun drawLine(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        baseline: Float
    ) {
        canvas.drawText(text, 0f, baseline, paint)
    }

    private const val widthDots = 384
    private const val heightDots = 232
}
