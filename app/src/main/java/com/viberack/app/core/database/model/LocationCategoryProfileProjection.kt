package com.viberack.app.core.database.model

data class LocationCategoryProfileProjection(
    val locationId: Long,
    val category: String?,
    val packageName: String?,
    val quantity: Int
)
