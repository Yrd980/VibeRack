package com.viberack.app.core.database.model

data class ContainerSlotStockProjection(
    val slotId: Long,
    val containerId: Long,
    val containerCode: String,
    val containerType: String,
    val slotNumber: Int,
    val slotCode: String,
    val slotDisplayName: String?,
    val sortOrder: Int,
    val stockItemId: Long?,
    val componentId: Long?,
    val partNumber: String?,
    val protocolPartId: String?,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val sourceUrl: String?,
    val specJson: String?,
    val imageLocalPath: String?,
    val quantity: Int?,
    val quantityState: String?,
    val safetyStockThreshold: Int?,
    val updatedAt: Long?
)
