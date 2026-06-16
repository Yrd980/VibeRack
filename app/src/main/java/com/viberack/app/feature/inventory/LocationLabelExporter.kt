package com.viberack.app.feature.inventory

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.viberack.app.R
import com.viberack.app.core.printer.PrintTypeface
import com.viberack.app.domain.model.StockLocationCell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocationLabelExporter {
    suspend fun createPreviewBitmap(
        context: Context,
        cell: StockLocationCell,
        inventoryCount: Int
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            createLabelBitmap(
                context = context,
                cell = cell,
                inventoryCount = inventoryCount
            )
        }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        locationCode: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = buildFileName(locationCode)
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VibeRack")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("无法创建相册文件。")

            resolver.openOutputStream(uri)?.use { outputStream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    "标签写入失败。"
                }
            } ?: error("无法打开相册输出流。")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            fileName
        }
    }

    private fun buildFileName(locationCode: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "location_label_${locationCode.trim()}_$timestamp.png"
    }

    private fun createLabelBitmap(
        context: Context,
        cell: StockLocationCell,
        inventoryCount: Int
    ): Bitmap {
        val width = 1200
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F5F7FA"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE3EA")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val mediaBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF2F6")
            style = Paint.Style.FILL
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 460f
            textAlign = Paint.Align.CENTER
            typeface = PrintTypeface.bold
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 74f
            typeface = PrintTypeface.bold
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B6470")
            textSize = 48f
            typeface = PrintTypeface.regular
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 48f
            typeface = PrintTypeface.regular
        }

        val outerPadding = 12f
        val cardRadius = 28f
        val contentPadding = 22f
        val sectionGap = 22f
        val mediaSize = height - outerPadding * 2f - contentPadding * 2f
        val cardLeft = outerPadding
        val cardTop = outerPadding
        val cardRight = width - outerPadding
        val cardBottom = height - outerPadding
        val mediaLeft = cardLeft + contentPadding
        val mediaTop = cardTop + contentPadding
        val mediaRight = mediaLeft + mediaSize
        val mediaBottom = mediaTop + mediaSize
        val qrInset = 18f
        val textLeft = mediaRight + sectionGap
        val textTop = mediaTop
        val textRight = cardRight - contentPadding
        val textWidth = (textRight - textLeft).toInt()
        val title = cell.displayName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.inventory_location_label_type)
        val subtitle = context.getString(R.string.inventory_location_label_type)
        val countLine = context.getString(R.string.inventory_location_label_count, inventoryCount)
        val quantityLine = context.getString(
            R.string.inventory_location_label_quantity,
            displayQuantityText(context, cell.totalQuantity)
        )

        canvas.drawRoundRect(
            RectF(cardLeft, cardTop, cardRight, cardBottom),
            cardRadius,
            cardRadius,
            cardPaint
        )
        canvas.drawRoundRect(
            RectF(cardLeft, cardTop, cardRight, cardBottom),
            cardRadius,
            cardRadius,
            borderPaint
        )
        canvas.drawRoundRect(
            RectF(mediaLeft, mediaTop, mediaRight, mediaBottom),
            22f,
            22f,
            mediaBackgroundPaint
        )
        val codeText = cell.code.trim().ifBlank { "-" }
        val codeInset = 36f
        codePaint.textSize = fitSingleLineTextSize(
            paint = codePaint,
            text = codeText,
            maxWidth = (mediaSize - codeInset * 2f).coerceAtLeast(1f),
            maxHeight = (mediaSize - codeInset * 2f).coerceAtLeast(1f),
            minSize = 120f,
            maxSize = 460f
        )
        val codeBaseline = mediaTop + mediaSize / 2f - (codePaint.descent() + codePaint.ascent()) / 2f
        canvas.drawText(
            codeText,
            mediaLeft + mediaSize / 2f,
            codeBaseline,
            codePaint
        )

        var currentY = textTop
        currentY += drawTextBlock(canvas, title, titlePaint, textLeft, currentY, textWidth, 3)
        currentY += 8f
        currentY += drawTextBlock(canvas, subtitle, subtitlePaint, textLeft, currentY, textWidth, 2)
        currentY += 10f
        currentY += drawTextBlock(canvas, countLine, bodyPaint, textLeft, currentY, textWidth, 2)
        currentY += 10f
        drawTextBlock(canvas, quantityLine, bodyPaint, textLeft, currentY, textWidth, 2)
        return bitmap
    }

    private fun displayQuantityText(context: Context, quantity: Int): String {
        return if (quantity == 0) {
            context.getString(R.string.inventory_unknown_quantity)
        } else {
            quantity.toString()
        }
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        maxLines: Int
    ): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun fitSingleLineTextSize(
        paint: TextPaint,
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        minSize: Float,
        maxSize: Float
    ): Float {
        if (text.isBlank()) {
            return minSize
        }

        var low = minSize
        var high = maxSize
        var best = minSize
        repeat(24) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val width = paint.measureText(text)
            val height = paint.fontMetrics.let { it.descent - it.ascent }
            if (width <= maxWidth && height <= maxHeight) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }
}
