package com.viberack.app.domain.model

data class ExistingStockLocation(
    val locationCode: String,
    val locationDisplayName: String?,
    val quantity: Int
)
