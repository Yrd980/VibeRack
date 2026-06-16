package com.viberack.app.domain.repository

import com.viberack.app.domain.model.ComponentBox
import com.viberack.app.domain.model.ComponentBoxLayer
import com.viberack.app.domain.model.ComponentDetail
import kotlinx.coroutines.flow.Flow

interface BoxRepository {
    fun observeBoxes(): Flow<List<ComponentBox>>
    fun observeLayers(boxId: Long): Flow<List<ComponentBoxLayer>>
    fun observeAllLayers(): Flow<List<ComponentBoxLayer>>
    fun observeEmptyLayers(): Flow<List<ComponentBoxLayer>>
    suspend fun createBox(code: String, name: String?, layerCount: Int): String?
    suspend fun findLayerByPosition(boxCode: String, layerCode: String): ComponentBoxLayer?
    suspend fun bindComponentToLayer(
        layerId: Long,
        component: ComponentDetail,
        quantity: Int,
        sourceType: String,
        rawPayload: String? = null
    ): String?
    suspend fun assignComponentToFirstEmptyLayer(
        component: ComponentDetail,
        quantity: Int,
        sourceType: String,
        rawPayload: String? = null
    ): ComponentBoxLayer?
}
