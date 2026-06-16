package com.viberack.app.core.database.model

data class BoxSummaryProjection(
    val id: Long,
    val code: String,
    val name: String?,
    val layerCount: Int,
    val occupiedLayerCount: Int
)
