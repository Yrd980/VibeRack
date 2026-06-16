package com.viberack.app.domain.model

data class ComponentDetail(
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val stockQuantity: Int?,
    val price: Double?,
    val productUrl: String?,
    val datasheetUrl: String?,
    val imageLocalPath: String? = null,
    val imageUrl: String?,
    val specifications: Map<String, String>
)
