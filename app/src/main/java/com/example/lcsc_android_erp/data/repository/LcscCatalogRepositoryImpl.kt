package com.example.lcsc_android_erp.data.repository

import android.util.Log
import com.example.lcsc_android_erp.data.remote.LcscCatalogRemoteDataSource
import com.example.lcsc_android_erp.data.remote.optDoubleOrNull
import com.example.lcsc_android_erp.data.remote.optIntOrNull
import com.example.lcsc_android_erp.data.remote.optStringOrNull
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.repository.LcscCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

class LcscCatalogRepositoryImpl(
    private val remoteDataSource: LcscCatalogRemoteDataSource
) : LcscCatalogRepository {
    private companion object {
        private const val TAG = "LcscCatalogParser"
    }

    private enum class NameStrategy {
        ExactPartNumber,
        SearchResult
    }

    override suspend fun lookupByPartNumber(partNumber: String): ComponentDetail? {
        return withContext(Dispatchers.IO) {
            val searchRecord = remoteDataSource.searchMatchedProduct(partNumber) ?: return@withContext null
            val product = searchRecord.optJSONObject("productVO") ?: return@withContext null
            val detail = buildComponentDetail(
                searchRecord = searchRecord,
                product = product,
                nameStrategy = NameStrategy.ExactPartNumber
            )
            logParsedDetail(source = "lookupByPartNumber", keyword = partNumber, detail = detail)
            detail
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<ComponentDetail> {
        return withContext(Dispatchers.IO) {
            val results = remoteDataSource.searchProducts(keyword)
                .mapNotNull { searchRecord ->
                    val product = searchRecord.optJSONObject("productVO") ?: return@mapNotNull null
                    buildComponentDetail(
                        searchRecord = searchRecord,
                        product = product,
                        nameStrategy = NameStrategy.SearchResult
                    )
                }
            results.take(10).forEachIndexed { index, detail ->
                logParsedDetail(
                    source = "searchByKeyword[$index]",
                    keyword = keyword,
                    detail = detail
                )
            }
            results
        }
    }

    private fun buildComponentDetail(
        searchRecord: JSONObject,
        product: JSONObject,
        nameStrategy: NameStrategy
    ): ComponentDetail {
        val searchParams = searchRecord.optJSONObject("paramLinkedMap")?.let { paramJson ->
            paramJson.keys().asSequence().mapNotNull { key ->
                val normalizedKey = sanitizeSearchText(key)
                val normalizedValue = sanitizeSearchText(paramJson.optString(key))
                if (normalizedKey == null || normalizedValue == null) {
                    null
                } else {
                    normalizedKey to normalizedValue
                }
            }.toMap()
        }.orEmpty()

        val datasheetUrl = extractDatasheetUrlFromSearch(product)

        val productUrl = product.optStringOrNull("productId")
            ?.let { id -> "https://item.szlcsc.com/$id.html" }

        val firstPrice = product.optJSONArray("productPriceList")
            ?.optJSONObject(0)
            ?.optDoubleOrNull("productPrice")

        val category = sanitizeSearchText(searchRecord.optStringOrNull("lightCatalogName"))
            ?: sanitizeSearchText(product.optStringOrNull("productType"))

        val lightProductModel = sanitizeSearchText(searchRecord.optStringOrNull("lightProductModel"))
        val productModel = sanitizeSearchText(product.optStringOrNull("productModel"))
        val lightProductName = sanitizeSearchText(searchRecord.optStringOrNull("lightProductName"))
        val productName = sanitizeSearchText(product.optStringOrNull("productName"))

        val normalizedName = when (nameStrategy) {
            NameStrategy.ExactPartNumber -> {
                lightProductModel
                    ?: productModel
                    ?: normalizeDisplayName(
                        rawName = lightProductName ?: productName,
                        fallbackName = category,
                        extractedSpecs = searchParams.values.toList()
                    )
            }

            NameStrategy.SearchResult -> {
                lightProductModel
                    ?: productModel
                    ?: normalizeDisplayName(
                        rawName = lightProductName ?: productName,
                        fallbackName = category,
                        extractedSpecs = searchParams.values.toList()
                    )
            }
        }

        return ComponentDetail(
            partNumber = product.optString("productCode"),
            mpn = productModel,
            name = normalizedName,
            brand = sanitizeSearchText(searchRecord.optStringOrNull("lightBrandName"))
                ?: sanitizeSearchText(product.optStringOrNull("productGradePlateName")),
            packageName = sanitizeSearchText(searchRecord.optStringOrNull("lightStandard"))
                ?: sanitizeSearchText(product.optStringOrNull("encapsulationModel")),
            category = category,
            description = sanitizeSearchText(product.optStringOrNull("remark"))
                ?: sanitizeSearchText(searchRecord.optStringOrNull("lightProductIntro")),
            stockQuantity = product.optIntOrNull("stockNumber")
                ?: product.optIntOrNull("validStockNumber"),
            price = firstPrice,
            productUrl = productUrl,
            datasheetUrl = datasheetUrl,
            imageUrl = product.optStringOrNull("breviaryImageUrl"),
            specifications = searchParams
        )
    }

    private fun normalizeDisplayName(
        rawName: String?,
        fallbackName: String?,
        extractedSpecs: List<String>
    ): String? {
        val baseName = rawName?.takeIf { it.isNotBlank() } ?: return fallbackName

        var candidate = baseName
        extractedSpecs
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .forEach { specValue ->
                candidate = candidate.replace(specValue, " ")
            }

        candidate = candidate
            .replace("\\s+".toRegex(), " ")
            .trim()

        return candidate.ifBlank { fallbackName ?: baseName }
    }

    private fun sanitizeSearchText(value: String?): String? {
        return value
            ?.let { Jsoup.parse(it).text() }
            ?.replace('\u00A0', ' ')
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractDatasheetUrlFromSearch(product: JSONObject): String? {
        val fileGroups = product.optJSONArray("fileTypeVOList") ?: return null
        for (index in 0 until fileGroups.length()) {
            val fileGroup = fileGroups.optJSONObject(index) ?: continue
            val details = fileGroup.optJSONArray("detailVOList") ?: continue
            val filePath = details.optJSONObject(0)?.optStringOrNull("fileUrl") ?: continue
            return "https://atta.szlcsc.com$filePath"
        }
        return null
    }

    private fun logParsedDetail(
        source: String,
        keyword: String,
        detail: ComponentDetail
    ) {
        Log.d(
            TAG,
            "parsed[$source]: keyword=$keyword, partNumber=${detail.partNumber}, name=${detail.name}, brand=${detail.brand}, package=${detail.packageName}, category=${detail.category}, secondAttrCount=${detail.specifications.size}, imageUrl=${detail.imageUrl}"
        )
    }
}
