package com.example.lcsc_android_erp.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup

class LcscCatalogRemoteDataSource(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "LcscCatalogRemote"
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
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val statusCode = response.code
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchNextData failed: code=$statusCode, url=$url")
                return null
            }

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                Log.w(TAG, "fetchNextData empty body: code=$statusCode, url=$url")
                return null
            }

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
    }
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
