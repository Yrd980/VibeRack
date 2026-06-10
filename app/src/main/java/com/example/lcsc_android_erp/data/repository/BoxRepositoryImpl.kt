package com.example.lcsc_android_erp.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.database.dao.BoxDao
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.LayerMaterialEntity
import com.example.lcsc_android_erp.core.database.model.BoxLayerProjection
import com.example.lcsc_android_erp.core.database.model.BoxSummaryProjection
import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class BoxRepositoryImpl(
    private val database: RoomDatabase,
    private val boxDao: BoxDao,
    private val componentDao: ComponentDao
) : BoxRepository {
    private companion object {
        private val BOX_CODE_REGEX = Regex("[A-Z0-9_-]+")
        private const val ERROR_INVALID_CODE = "invalid_code"
        private const val ERROR_INVALID_LAYER_COUNT = "invalid_layer_count"
        private const val ERROR_DUPLICATE_CODE = "duplicate_code"
        private const val ERROR_INVALID_QUANTITY = "invalid_quantity"
        private const val ERROR_LAYER_NOT_FOUND = "layer_not_found"
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

    override fun observeAllLayers(): Flow<List<ComponentBoxLayer>> {
        return boxDao.observeAllLayers().map { layers ->
            layers.map(::toComponentBoxLayer)
        }
    }

    override fun observeEmptyLayers(): Flow<List<ComponentBoxLayer>> {
        return boxDao.observeEmptyLayers().map { layers ->
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

    override suspend fun findLayerByPosition(
        boxCode: String,
        layerCode: String
    ): ComponentBoxLayer? {
        val normalizedBoxCode = boxCode.trim().uppercase(Locale.ROOT)
        val normalizedLayerCode = layerCode.trim().uppercase(Locale.ROOT)
        if (normalizedBoxCode.isBlank() || normalizedLayerCode.isBlank()) {
            return null
        }
        return boxDao.findLayerByPosition(normalizedBoxCode, normalizedLayerCode)
            ?.let(::toComponentBoxLayer)
    }

    override suspend fun bindComponentToLayer(
        layerId: Long,
        component: ComponentDetail,
        quantity: Int,
        sourceType: String,
        rawPayload: String?
    ): String? {
        if (quantity < 0) {
            return ERROR_INVALID_QUANTITY
        }

        return database.withTransaction<String?> {
            val layer = boxDao.findLayerById(layerId)
                ?: return@withTransaction ERROR_LAYER_NOT_FOUND
            val componentId = upsertComponent(component)
            val now = System.currentTimeMillis()
            boxDao.replaceLayerMaterial(
                LayerMaterialEntity(
                    layerId = layer.id,
                    componentId = componentId,
                    quantity = quantity,
                    sourceType = sourceType,
                    rawPayload = rawPayload,
                    createdAt = now,
                    updatedAt = now
                )
            )
            null
        }
    }

    override suspend fun assignComponentToFirstEmptyLayer(
        component: ComponentDetail,
        quantity: Int,
        sourceType: String,
        rawPayload: String?
    ): ComponentBoxLayer? {
        if (quantity < 0) {
            return null
        }

        return database.withTransaction<ComponentBoxLayer?> {
            val layer = boxDao.findFirstEmptyLayer() ?: return@withTransaction null
            val componentId = upsertComponent(component)
            val now = System.currentTimeMillis()
            boxDao.replaceLayerMaterial(
                LayerMaterialEntity(
                    layerId = layer.id,
                    componentId = componentId,
                    quantity = quantity,
                    sourceType = sourceType,
                    rawPayload = rawPayload,
                    createdAt = now,
                    updatedAt = now
                )
            )
            boxDao.findLayerById(layer.id)?.let(::toComponentBoxLayer)
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

    private suspend fun upsertComponent(component: ComponentDetail): Long {
        val normalizedPartNumber = component.partNumber.trim().uppercase(Locale.ROOT)
        val specJson = component.specifications
            .takeIf { it.isNotEmpty() }
            ?.let { JSONObject(it).toString() }
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        if (existing != null) {
            val updated = existing.copy(
                partNumber = normalizedPartNumber,
                mpn = existing.mpn ?: component.mpn,
                name = existing.name ?: component.name,
                brand = existing.brand ?: component.brand,
                packageName = existing.packageName ?: component.packageName,
                category = existing.category ?: component.category,
                specJson = existing.specJson ?: specJson,
                description = existing.description ?: component.description,
                sourceUrl = existing.sourceUrl ?: component.productUrl,
                imageLocalPath = existing.imageLocalPath ?: component.imageLocalPath,
                updatedAt = System.currentTimeMillis()
            )
            if (updated != existing) {
                componentDao.update(updated)
            }
            return existing.id
        }

        val insertId = componentDao.insert(
            ComponentEntity(
                partNumber = normalizedPartNumber,
                mpn = component.mpn,
                name = component.name,
                brand = component.brand,
                packageName = component.packageName,
                category = component.category,
                specJson = specJson,
                description = component.description,
                sourceUrl = component.productUrl,
                imageLocalPath = component.imageLocalPath
            )
        )
        if (insertId > 0) {
            return insertId
        }
        return componentDao.findByPartNumber(normalizedPartNumber)?.id
            ?: error("Failed to resolve component id for $normalizedPartNumber")
    }
}
