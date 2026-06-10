package com.example.lcsc_android_erp.feature.boxes

import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer

data class BoxesUiState(
    val boxes: List<ComponentBox> = emptyList(),
    val selectedBox: ComponentBox? = null,
    val selectedBoxLayers: List<ComponentBoxLayer> = emptyList(),
    val createError: String? = null
)
