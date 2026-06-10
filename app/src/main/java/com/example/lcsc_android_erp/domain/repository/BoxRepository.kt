package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import kotlinx.coroutines.flow.Flow

interface BoxRepository {
    fun observeBoxes(): Flow<List<ComponentBox>>
    fun observeLayers(boxId: Long): Flow<List<ComponentBoxLayer>>
    suspend fun createBox(code: String, name: String?, layerCount: Int): String?
}
