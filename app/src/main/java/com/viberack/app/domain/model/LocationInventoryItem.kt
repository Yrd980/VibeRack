package com.viberack.app.domain.model

data class LocationInventoryItem(
    val inventoryItemId: Long,
    val componentId: Long,
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String? = null,
    val sourceUrl: String? = null,
    val specifications: Map<String, String> = emptyMap(),
    val imageLocalPath: String? = null,
    val imageUrl: String? = null,
    val quantity: Int,
    val lastInboundAt: Long
)
