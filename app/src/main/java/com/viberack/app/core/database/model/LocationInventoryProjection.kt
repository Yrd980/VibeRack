package com.viberack.app.core.database.model

data class LocationInventoryProjection(
    val inventoryItemId: Long,
    val componentId: Long,
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val sourceUrl: String?,
    val specJson: String?,
    val imageLocalPath: String?,
    val quantity: Int,
    val lastInboundAt: Long
)
