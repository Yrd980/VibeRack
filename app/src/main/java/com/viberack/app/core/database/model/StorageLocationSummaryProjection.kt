package com.viberack.app.core.database.model

data class StorageLocationSummaryProjection(
    val id: Long,
    val code: String,
    val displayName: String?,
    val colorHex: String?,
    val sortMode: String,
    val remark: String?,
    val inventoryItemCount: Int,
    val totalQuantity: Int
)
