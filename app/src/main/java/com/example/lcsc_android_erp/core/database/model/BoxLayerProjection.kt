package com.example.lcsc_android_erp.core.database.model

data class BoxLayerProjection(
    val id: Long,
    val boxId: Long,
    val boxCode: String,
    val layerCode: String,
    val displayName: String?,
    val sortOrder: Int,
    val componentId: Long?,
    val partNumber: String?,
    val componentName: String?,
    val quantity: Int?
)
