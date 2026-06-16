package com.viberack.app.feature.inbound

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

private const val MANUAL_INBOUND_IMAGE_MAX_DIMENSION = 300

fun saveManualInboundBitmap(
    context: Context,
    partNumber: String,
    bitmap: Bitmap
): String {
    val imageDir = manualInboundImageDir(context)
    val targetFile = buildManualInboundImageFile(
        imageDir = imageDir,
        partNumber = partNumber,
        extension = "jpg"
    )
    saveBitmapToFile(
        bitmap = resizeBitmapWithinLimit(bitmap),
        targetFile = targetFile,
        format = Bitmap.CompressFormat.JPEG
    )
    return targetFile.absolutePath
}

fun saveManualInboundImageUri(
    context: Context,
    partNumber: String,
    uri: Uri
): String {
    val imageDir = manualInboundImageDir(context)
    val (extension, format) = when (context.contentResolver.getType(uri)?.lowercase()) {
        "image/png" -> "png" to Bitmap.CompressFormat.PNG
        "image/webp" -> "webp" to Bitmap.CompressFormat.WEBP_LOSSY
        else -> "jpg" to Bitmap.CompressFormat.JPEG
    }
    val targetFile = buildManualInboundImageFile(
        imageDir = imageDir,
        partNumber = partNumber,
        extension = extension
    )
    val bitmap = decodeBitmapWithinLimit(context, uri)
        ?: error("Failed to decode selected image")
    saveBitmapToFile(
        bitmap = bitmap,
        targetFile = targetFile,
        format = format
    )
    return targetFile.absolutePath
}

private fun manualInboundImageDir(context: Context): File {
    return File(context.filesDir, "manual_inbound_images").apply { mkdirs() }
}

private fun buildManualInboundImageFile(
    imageDir: File,
    partNumber: String,
    extension: String
): File {
    val safePartNumber = partNumber
        .trim()
        .uppercase()
        .ifBlank { "MANUAL" }
        .replace(Regex("[^A-Z0-9._-]"), "_")
    val normalizedExtension = extension.lowercase().ifBlank { "jpg" }
    return File(imageDir, "${safePartNumber}_${System.currentTimeMillis()}.$normalizedExtension")
}

private fun decodeBitmapWithinLimit(
    context: Context,
    uri: Uri,
    maxDimension: Int = MANUAL_INBOUND_IMAGE_MAX_DIMENSION
): Bitmap? {
    return decodeBitmapWithBitmapFactory(
        context = context,
        uri = uri,
        maxDimension = maxDimension
    ) ?: decodeBitmapWithImageDecoder(
        context = context,
        uri = uri,
        maxDimension = maxDimension
    )
}

private fun decodeBitmapWithBitmapFactory(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    resolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, bounds)
    } ?: return null

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = maxDimension
        )
    }
    val decodedBitmap = resolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, sampledOptions)
    } ?: return null

    return resizeBitmapWithinLimit(decodedBitmap, maxDimension)
}

private fun decodeBitmapWithImageDecoder(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    return runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sampleSize = calculateInSampleSize(
                width = info.size.width,
                height = info.size.height,
                maxDimension = maxDimension
            )
            decoder.setTargetSampleSize(sampleSize)
        }
        resizeBitmapWithinLimit(bitmap, maxDimension)
    }.getOrNull()
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int
): Int {
    if (width <= 0 || height <= 0 || (width <= maxDimension && height <= maxDimension)) {
        return 1
    }
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth > maxDimension || sampledHeight > maxDimension) {
        sampleSize *= 2
        sampledWidth = width / sampleSize
        sampledHeight = height / sampleSize
    }
    return sampleSize.coerceAtLeast(1)
}

private fun resizeBitmapWithinLimit(
    bitmap: Bitmap,
    maxDimension: Int = MANUAL_INBOUND_IMAGE_MAX_DIMENSION
): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }
    val scale = minOf(
        maxDimension.toFloat() / width.toFloat(),
        maxDimension.toFloat() / height.toFloat()
    )
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun saveBitmapToFile(
    bitmap: Bitmap,
    targetFile: File,
    format: Bitmap.CompressFormat
) {
    FileOutputStream(targetFile).use { outputStream ->
        val quality = when (format) {
            Bitmap.CompressFormat.PNG -> 100
            else -> 92
        }
        if (!bitmap.compress(format, quality, outputStream)) {
            error("Failed to compress bitmap")
        }
        outputStream.flush()
    }
}
