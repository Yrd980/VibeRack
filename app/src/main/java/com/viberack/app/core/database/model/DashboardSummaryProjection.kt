package com.viberack.app.core.database.model

data class DashboardSummaryProjection(
    val componentCount: Int,
    val locationCount: Int,
    val inventoryCount: Int,
    val totalQuantity: Int,
    val transactionCount: Int
)
