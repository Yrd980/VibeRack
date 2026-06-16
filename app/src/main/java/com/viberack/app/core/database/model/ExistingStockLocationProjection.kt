package com.viberack.app.core.database.model

data class ExistingStockLocationProjection(
    val locationCode: String,
    val locationDisplayName: String?,
    val quantity: Int
)
