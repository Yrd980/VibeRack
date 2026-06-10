package com.example.lcsc_android_erp.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.database.dao.BoxDao
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.model.BoxLayerProjection
import com.example.lcsc_android_erp.core.database.model.BoxSummaryProjection
import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BoxRepositoryImpl(
    private val database: RoomDatabase,
    private val boxDao: BoxDao
) : BoxRepository {
    private companion object {
        private val BOX_CODE_REGEX = Regex("[A-Z0-9_-]+")
        private const val ERROR_INVALID_CODE = "invalid_code"
        private const val ERROR_INVALID_LAYER_COUNT = "invalid_layer_count"
        private const val ERROR_DUPLICATE_CODE = "duplicate_code"
    }

    override fun observeBoxes(): Flow<List<ComponentBox>> {
        return boxDao.observeBoxSummaries().map { boxes ->
            boxes.map(::toComponentBox)
        }
    }

    override fun observeLayers(boxId: Long): Flow<List<ComponentBoxLayer>> {
        return boxDao.observeLayersForBox(boxId).map { layers ->
            layers.map(::toComponentBoxLayer)
        }
    }

    override suspend fun createBox(code: String, name: String?, layerCount: Int): String? {
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        if (!BOX_CODE_REGEX.matches(normalizedCode)) {
            return ERROR_INVALID_CODE
        }
        if (layerCount !in 1..99) {
            return ERROR_INVALID_LAYER_COUNT
        }

        return database.withTransaction<String?> {
            val now = System.currentTimeMillis()
            val boxId = boxDao.insertBox(
                BoxEntity(
                    code = normalizedCode,
                    name = name?.trim()?.ifBlank { null },
                    layerCount = layerCount,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (boxId <= 0) {
                return@withTransaction ERROR_DUPLICATE_CODE
            }

            boxDao.insertLayers(
                (1..layerCount).map { index ->
                    val layerCode = "L%02d".format(Locale.ROOT, index)
                    BoxLayerEntity(
                        boxId = boxId,
                        layerCode = layerCode,
                        displayName = layerCode,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            )
            null
        }
    }

    private fun toComponentBox(projection: BoxSummaryProjection): ComponentBox {
        return ComponentBox(
            id = projection.id,
            code = projection.code,
            name = projection.name,
            layerCount = projection.layerCount,
            occupiedLayerCount = projection.occupiedLayerCount
        )
    }

    private fun toComponentBoxLayer(projection: BoxLayerProjection): ComponentBoxLayer {
        return ComponentBoxLayer(
            id = projection.id,
            boxId = projection.boxId,
            boxCode = projection.boxCode,
            layerCode = projection.layerCode,
            displayName = projection.displayName,
            sortOrder = projection.sortOrder,
            componentId = projection.componentId,
            partNumber = projection.partNumber,
            componentName = projection.componentName,
            quantity = projection.quantity
        )
    }
}
