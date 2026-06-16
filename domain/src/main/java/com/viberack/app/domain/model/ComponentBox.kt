package com.viberack.app.domain.model

data class ComponentBox(
    val id: Long,
    val code: String,
    val name: String?,
    val layerCount: Int,
    val occupiedLayerCount: Int
)
