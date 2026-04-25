package com.example.lcsc_android_erp.domain.model

data class SearchInventoryRecord(
    val inventoryItemId: Long,
    val componentId: Long,
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val sourceUrl: String? = null,
    val specifications: Map<String, String> = emptyMap(),
    val imageLocalPath: String? = null,
    val quantity: Int,
    val locationId: Long,
    val locationCode: String,
    val locationDisplayName: String?,
    val locationColorHex: String?
)
