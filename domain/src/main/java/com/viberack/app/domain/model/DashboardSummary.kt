package com.viberack.app.domain.model

data class DashboardSummary(
    val componentCount: Int = 0,
    val locationCount: Int = 0,
    val inventoryCount: Int = 0,
    val totalQuantity: Int = 0,
    val transactionCount: Int = 0
)
