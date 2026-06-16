package com.viberack.app.feature.inventory

data class InventoryOpenRequest(
    val locationCode: String? = null,
    val partNumber: String? = null
)
