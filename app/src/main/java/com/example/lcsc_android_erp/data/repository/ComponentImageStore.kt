package com.example.lcsc_android_erp.data.repository

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ComponentImageStore(
    context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val imageDir = File(context.filesDir, "component_images").apply {
        mkdirs()
    }

    suspend fun persistImage(partNumber: String, imageUrl: String?): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val targetFile = File(imageDir, buildFileName(partNumber, normalizedUrl))
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext targetFile.absolutePath
        }

        val request = Request.Builder()
            .url(normalizedUrl)
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use
                }
                val body = response.body ?: return@use
                targetFile.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.getOrNull()

        targetFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    suspend fun persistImageBytes(
        partNumber: String,
        sourceName: String?,
        bytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            return@withContext null
        }
        val targetFile = File(imageDir, buildFileName(partNumber, sourceName ?: "$partNumber.jpg"))
        runCatching {
            targetFile.outputStream().use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }
        }.getOrNull()
        targetFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    private fun buildFileName(partNumber: String, imageUrl: String): String {
        val safePartNumber = partNumber.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val extension = imageUrl
            .substringBefore('?')
            .substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it.length in 2..5 }
            ?: "jpg"
        return "$safePartNumber.$extension"
    }
}
