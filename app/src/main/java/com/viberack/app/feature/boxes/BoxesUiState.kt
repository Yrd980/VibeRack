package com.viberack.app.feature.boxes

import com.viberack.app.domain.model.ComponentBox
import com.viberack.app.domain.model.ComponentBoxLayer

data class BoxesUiState(
    val boxes: List<ComponentBox> = emptyList(),
    val selectedBox: ComponentBox? = null,
    val selectedBoxLayers: List<ComponentBoxLayer> = emptyList(),
    val highlightedLayerId: Long? = null,
    val createError: String? = null
)
