package com.example.lcsc_android_erp.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.database.dao.BoxDao
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.entity.LayerMaterialEntity
import com.example.lcsc_android_erp.core.database.model.BoxLayerProjection
import com.example.lcsc_android_erp.core.database.model.BoxSummaryProjection
import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockOperation
import com.example.lcsc_android_erp.domain.model.StockOperationType
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementWrite
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class BoxRepositoryImpl(
    private val database: RoomDatabase,
    private val boxDao: BoxDao,
    private val componentDao: ComponentDao,
    private val containerDao: ContainerDao,
    private val stockPlacementRepository: StockPlacementRepository,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) : BoxRepository {
    private companion object {
        private val BOX_CODE_REGEX = Regex("[A-Z0-9_-]+")
        private const val BOX_CONTAINER_ID_OFFSET = 1_000_000_000L
        private const val BOX_SLOT_ID_OFFSET = 2_000_000_000L
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
            if (containerDao.findContainerByCode(normalizedCode) != null) {
                return@withTransaction ERROR_DUPLICATE_CODE
            }
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

            val layers = (1..layerCount).map { index ->
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
            boxDao.insertLayers(layers)
            val insertedLayers = boxDao.getLayerEntitiesForBox(boxId)
            ensureBoxContainer(
                box = BoxEntity(
                    id = boxId,
                    code = normalizedCode,
                    name = name?.trim()?.ifBlank { null },
                    layerCount = layerCount,
                    createdAt = now,
                    updatedAt = now
                ),
                layers = insertedLayers
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
            val box = boxDao.findBoxById(layer.boxId)
                ?: return@withTransaction ERROR_LAYER_NOT_FOUND
            val slot = ensureBoxSlot(
                box = box,
                layer = BoxLayerEntity(
                    id = layer.id,
                    boxId = layer.boxId,
                    layerCode = layer.layerCode,
                    displayName = layer.displayName,
                    sortOrder = layer.sortOrder,
                    updatedAt = now
                )
            )
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
            replaceSlotStock(
                containerId = boxContainerId(box.id),
                slotId = slot.id,
                componentId = componentId,
                quantity = quantity,
                sourceType = sourceType,
                sourceRef = component.partNumber,
                rawPayload = rawPayload,
                updatedAt = now
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
            val box = boxDao.findBoxById(layer.boxId) ?: return@withTransaction null
            val slot = ensureBoxSlot(
                box = box,
                layer = BoxLayerEntity(
                    id = layer.id,
                    boxId = layer.boxId,
                    layerCode = layer.layerCode,
                    displayName = layer.displayName,
                    sortOrder = layer.sortOrder,
                    updatedAt = now
                )
            )
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
            replaceSlotStock(
                containerId = boxContainerId(box.id),
                slotId = slot.id,
                componentId = componentId,
                quantity = quantity,
                sourceType = sourceType,
                sourceRef = component.partNumber,
                rawPayload = rawPayload,
                updatedAt = now
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

    private suspend fun ensureBoxContainer(
        box: BoxEntity,
        layers: List<BoxLayerEntity>
    ) {
        val containerId = boxContainerId(box.id)
        val now = System.currentTimeMillis()
        val existing = containerDao.findContainerById(containerId)
        val container = ContainerEntity(
            id = containerId,
            code = box.code,
            displayName = box.name ?: box.code,
            type = ContainerType.BOX.name,
            slotCount = box.layerCount,
            createdAt = box.createdAt,
            updatedAt = now
        )
        if (existing == null) {
            containerDao.insertContainer(container)
        } else {
            containerDao.updateContainer(
                existing.copy(
                    code = box.code,
                    displayName = box.name ?: box.code,
                    type = ContainerType.BOX.name,
                    slotCount = box.layerCount,
                    updatedAt = now
                )
            )
        }
        layers.forEach { layer ->
            ensureBoxSlot(box, layer)
        }
    }

    private suspend fun ensureBoxSlot(
        box: BoxEntity,
        layer: BoxLayerEntity
    ): ContainerSlotEntity {
        val containerId = boxContainerId(box.id)
        if (containerDao.findContainerById(containerId) == null) {
            ensureBoxContainer(box, emptyList())
        }
        val now = System.currentTimeMillis()
        val slot = containerDao.findSlotByContainerAndNumber(containerId, layer.sortOrder)
        if (slot != null) {
            val updatedSlot = slot.copy(
                slotCode = layer.layerCode,
                displayName = layer.displayName ?: layer.layerCode,
                sortOrder = layer.sortOrder,
                updatedAt = now
            )
            if (updatedSlot != slot) {
                containerDao.updateSlot(updatedSlot)
            }
            return updatedSlot
        }
        val newSlot = ContainerSlotEntity(
            id = boxSlotId(layer.id),
            containerId = containerId,
            slotNumber = layer.sortOrder,
            slotCode = layer.layerCode,
            displayName = layer.displayName ?: layer.layerCode,
            sortOrder = layer.sortOrder,
            createdAt = layer.createdAt,
            updatedAt = now
        )
        val insertId = containerDao.insertSlot(newSlot)
        return containerDao.findSlotByContainerAndNumber(containerId, layer.sortOrder)
            ?: newSlot.copy(id = if (insertId > 0) insertId else 0)
    }

    private suspend fun replaceSlotStock(
        containerId: Long,
        slotId: Long,
        componentId: Long,
        quantity: Int,
        sourceType: String,
        sourceRef: String,
        rawPayload: String?,
        updatedAt: Long
    ) {
        stockPlacementRepository.replaceSlotStock(
            StockPlacementWrite(
                componentId = componentId,
                containerId = containerId,
                slotId = slotId,
                quantity = quantity,
                lastInboundAt = updatedAt,
                updatedAt = updatedAt
            )
        )
        stockPlacementRepository.recordOperation(
            StockOperation(
                type = StockOperationType.INBOUND,
                containerId = containerId,
                slotId = slotId,
                componentId = componentId,
                quantityDelta = quantity,
                sourceType = sourceType,
                sourceRef = sourceRef,
                rawPayload = rawPayload,
                createdAt = updatedAt
            )
        )
    }

    private fun boxContainerId(boxId: Long): Long = BOX_CONTAINER_ID_OFFSET + boxId

    private fun boxSlotId(layerId: Long): Long = BOX_SLOT_ID_OFFSET + layerId

    private suspend fun upsertComponent(component: ComponentDetail): Long {
        val normalizedPartNumber = component.partNumber.trim().uppercase(Locale.ROOT)
        val specJson = component.specifications
            .takeIf { it.isNotEmpty() }
            ?.let { JSONObject(it).toString() }
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        if (existing != null) {
            val protocolPartId = existing.protocolPartId
                ?: protocolPartIdStrategy.forComponent(existing.id, normalizedPartNumber)
            val updated = existing.copy(
                partNumber = normalizedPartNumber,
                protocolPartId = protocolPartId,
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
                protocolPartId = protocolPartIdStrategy.forComponent(null, normalizedPartNumber),
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
            val resolvedProtocolPartId = protocolPartIdStrategy.forComponent(insertId, normalizedPartNumber)
            val insertedProtocolPartId = protocolPartIdStrategy.forComponent(null, normalizedPartNumber)
            if (resolvedProtocolPartId != insertedProtocolPartId) {
                componentDao.update(
                    ComponentEntity(
                        id = insertId,
                        partNumber = normalizedPartNumber,
                        protocolPartId = resolvedProtocolPartId,
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
            }
            return insertId
        }
        return componentDao.findByPartNumber(normalizedPartNumber)?.id
            ?: error("Failed to resolve component id for $normalizedPartNumber")
    }

}
