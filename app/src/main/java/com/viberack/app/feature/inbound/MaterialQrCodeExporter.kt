package com.viberack.app.feature.inbound

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
import com.viberack.app.domain.model.ComponentDetail
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MaterialQrCodeExporter {
    suspend fun createPreviewBitmap(
        context: Context,
        component: ComponentDetail
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = "{on:,pc:${component.partNumber.trim()},pm:,qty:,mc:,cc:,pdi:,hp:}"
            createLabelBitmap(
                context = context,
                component = component,
                qrBitmap = createQrBitmap(payload, size = 720)
            )
        }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        partNumber: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = buildFileName(partNumber)
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/VibeRack"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("无法创建相册文件。")

            resolver.openOutputStream(uri)?.use { outputStream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    "二维码写入失败。"
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

    suspend fun saveToGallery(
        context: Context,
        component: ComponentDetail
    ): Result<String> = withContext(Dispatchers.IO) {
        createPreviewBitmap(context, component).fold(
            onSuccess = { bitmap -> saveBitmapToGallery(context, component.partNumber, bitmap) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun buildFileName(partNumber: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "material_qr_${partNumber.trim()}_$timestamp.png"
    }

    private fun createLabelBitmap(
        context: Context,
        component: ComponentDetail,
        qrBitmap: Bitmap
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
        val subtitle = listOfNotNull(component.brand, component.packageName, component.category)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val secondarySummary = exporterSecondarySummary(component)
        val detailLine = "${context.getString(R.string.inbound_component_number)}: ${component.partNumber}"

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

        canvas.drawBitmap(
            qrBitmap,
            null,
            RectF(
                mediaLeft + qrInset,
                mediaTop + qrInset,
                mediaRight - qrInset,
                mediaBottom - qrInset
            ),
            null
        )

        var currentY = textTop
        currentY += drawTextBlock(
            canvas = canvas,
            text = component.name ?: component.mpn ?: component.partNumber,
            paint = titlePaint,
            x = textLeft,
            y = currentY,
            width = textWidth,
            maxLines = 3
        )
        if (subtitle.isNotBlank()) {
            currentY += 8f
            currentY += drawTextBlock(
                canvas = canvas,
                text = subtitle,
                paint = subtitlePaint,
                x = textLeft,
                y = currentY,
                width = textWidth,
                maxLines = 3
            )
        }
        secondarySummary?.let {
            currentY += 10f
            currentY += drawTextBlock(
                canvas = canvas,
                text = it,
                paint = bodyPaint,
                x = textLeft,
                y = currentY,
                width = textWidth,
                maxLines = 3
            )
        }
        currentY += 12f
        drawTextBlock(
            canvas = canvas,
            text = detailLine,
            paint = bodyPaint,
            x = textLeft,
            y = currentY,
            width = textWidth,
            maxLines = 3
        )
        return bitmap
    }

    private fun createQrBitmap(content: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
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

    private fun exporterSecondarySummary(component: ComponentDetail): String? {
        val preferredKeys = listOf("电阻类型", "阻值", "容值", "精度", "功率")
        return buildList {
            preferredKeys.forEach { key ->
                component.specifications[key]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::add)
            }
            component.specifications
                .filterKeys { it !in preferredKeys }
                .toSortedMap()
                .values
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .forEach(::add)
        }.distinct().joinToString(" · ").takeIf { it.isNotBlank() }
    }
}
