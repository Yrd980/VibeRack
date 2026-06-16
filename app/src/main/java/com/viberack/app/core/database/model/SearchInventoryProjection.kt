package com.viberack.app.core.database.model

data class SearchInventoryProjection(
    val inventoryItemId: Long,
    val legacyInventoryItemId: Long?,
    val stockItemId: Long,
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
    val locationId: Long,
    val locationCode: String,
    val locationDisplayName: String?,
    val locationColorHex: String?,
    val containerType: String,
    val containerMacAddress: String?,
    val slotId: Long,
    val slotNumber: Int,
    val slotCode: String,
    val slotDisplayName: String?
)
