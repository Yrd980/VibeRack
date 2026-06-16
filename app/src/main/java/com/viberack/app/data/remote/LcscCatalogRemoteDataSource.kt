package com.viberack.app.data.remote

import android.util.Log
import java.util.Base64
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

class LcscCatalogRemoteDataSource(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "LcscCatalogRemote"
        private const val VERIFICATION_KEY = "tg09It3*9h"
        private val VERIFICATION_TOKENS = listOf("_xvasu", "_xvtsc", "_xvpfs", "_xvpts")
    }

    fun searchProducts(keyword: String): List<JSONObject> {
        val url = "https://so.szlcsc.com/global.html".toHttpUrl()
            .newBuilder()
            .addQueryParameter("k", keyword)
            .build()
            .toString()
        val root = fetchNextData(url) ?: return emptyList()
        val productList = root
            .optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("soData")
            ?.optJSONObject("searchResult")
            ?.optJSONArray("productRecordList")
            ?: return emptyList()

        return buildList {
            for (index in 0 until productList.length()) {
                productList.optJSONObject(index)?.let(::add)
            }
        }
    }

    fun searchMatchedProduct(partNumber: String): JSONObject? {
        val normalizedPartNumber = partNumber.trim().uppercase()
        for (record in searchProducts(partNumber)) {
            val productCode = record
                .optJSONObject("productVO")
                ?.optStringOrNull("productCode")
                ?.trim()
                ?.uppercase()
                ?: continue
            if (productCode == normalizedPartNumber) {
                return record
            }
        }

        return null
    }

    private fun fetchNextData(url: String): JSONObject? {
        return try {
            var result = fetchHtml(url)
            if (result == null) {
                return null
            }

            if (result.statusCode == 203 && looksLikeVerificationPage(result.html)) {
                val verificationCookie = buildVerificationCookie(result.html)
                if (verificationCookie == null) {
                    Log.w(TAG, "fetchNextData verification cookie parse failed: url=$url")
                    return null
                }

                result = fetchHtml(url, verificationCookie)
                if (result == null) {
                    return null
                }
            }

            parseNextData(url = url, statusCode = result.statusCode, html = result.html)
        } catch (error: IOException) {
            Log.w(TAG, "fetchNextData network failed: url=$url", error)
            null
        } catch (error: JSONException) {
            Log.w(TAG, "fetchNextData parse failed: url=$url", error)
            null
        }
    }

    private fun fetchHtml(url: String, cookie: String? = null): FetchHtmlResult? {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Referer", "https://so.szlcsc.com/")
            .apply {
                if (cookie != null) {
                    header("Cookie", cookie)
                }
            }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val statusCode = response.code
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchHtml failed: code=$statusCode, url=$url")
                return null
            }

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                Log.w(TAG, "fetchHtml empty body: code=$statusCode, url=$url")
                return null
            }

            return FetchHtmlResult(statusCode = statusCode, html = html)
        }
    }

    private fun parseNextData(url: String, statusCode: Int, html: String): JSONObject? {
        val document = Jsoup.parse(html)
        val nextData = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (nextData == null) {
            Log.w(
                TAG,
                "fetchNextData missing __NEXT_DATA__: code=$statusCode, url=$url, htmlPreview=${html.take(200)}"
            )
            return null
        }

        val root = JSONObject(nextData)
        val productCount = root
            .optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("soData")
            ?.optJSONObject("searchResult")
            ?.optJSONArray("productRecordList")
            ?.length()
            ?: 0

        Log.d(
            TAG,
            "fetchNextData success: code=$statusCode, url=$url, productCount=$productCount"
        )
        return root
    }

    private fun looksLikeVerificationPage(html: String): Boolean {
        return VERIFICATION_TOKENS.any { token -> html.contains(token) }
    }

    private fun buildVerificationCookie(html: String): String? {
        val xvasu = extractJavascriptVariable(html, "_xvasu") ?: return null
        val xvpts = extractJavascriptVariable(html, "_xvpts") ?: return null
        val xvpfs = extractJavascriptVariable(html, "_xvpfs") ?: return null
        val cookieName = "$xvpfs$xvasu"
        val encryptedValue = rc4(VERIFICATION_KEY, "$xvpts:$xvasu")
        val cookieValue = Base64.getEncoder().encodeToString(encryptedValue)
        return "$cookieName=$cookieValue"
    }

    private fun extractJavascriptVariable(html: String, name: String): String? {
        val pattern = Regex("""var\s+${Regex.escape(name)}\s*=\s*(.+?);""")
        val value = pattern.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return value
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    private fun rc4(key: String, value: String): ByteArray {
        val keyBytes = key.encodeToByteArray()
        val valueBytes = value.encodeToByteArray()
        val state = IntArray(256) { it }
        var indexB = 0

        for (indexA in state.indices) {
            indexB = (indexB + state[indexA] + keyBytes[indexA % keyBytes.size].toIntUnsigned()) % 256
            state.swap(indexA, indexB)
        }

        var indexA = 0
        indexB = 0
        val output = ByteArray(valueBytes.size)
        for (index in valueBytes.indices) {
            indexA = (indexA + 1) % 256
            indexB = (indexB + state[indexA]) % 256
            state.swap(indexA, indexB)
            val keyStream = state[(state[indexA] + state[indexB]) % 256]
            output[index] = (valueBytes[index].toIntUnsigned() xor keyStream).toByte()
        }

        return output
    }

    private fun IntArray.swap(first: Int, second: Int) {
        val original = this[first]
        this[first] = this[second]
        this[second] = original
    }

    private fun Byte.toIntUnsigned(): Int = toInt() and 0xFF

    private data class FetchHtmlResult(
        val statusCode: Int,
        val html: String
    )
}

internal fun JSONObject.optStringOrNull(name: String): String? {
    val value = optString(name)
    return value.takeIf { it.isNotBlank() && it != "null" }
}

internal fun JSONObject.optIntOrNull(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

internal fun JSONObject.optDoubleOrNull(name: String): Double? {
    return if (has(name) && !isNull(name)) optDouble(name) else null
}
