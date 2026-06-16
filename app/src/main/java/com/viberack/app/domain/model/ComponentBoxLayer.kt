package com.viberack.app.domain.model

data class ComponentBoxLayer(
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
) {
    val positionCode: String
        get() = "$boxCode-$layerCode"

    val isOccupied: Boolean
        get() = partNumber != null
}
