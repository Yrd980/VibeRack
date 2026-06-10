package com.example.lcsc_android_erp.domain.model

data class ComponentBox(
    val id: Long,
    val code: String,
    val name: String?,
    val layerCount: Int,
    val occupiedLayerCount: Int
)
