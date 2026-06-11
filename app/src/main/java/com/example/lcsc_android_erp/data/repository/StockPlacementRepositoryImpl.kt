package com.example.lcsc_android_erp.data.repository

import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.dao.StockItemDao
import com.example.lcsc_android_erp.core.database.dao.StockOperationDao
import com.example.lcsc_android_erp.core.database.entity.StockItemEntity
import com.example.lcsc_android_erp.core.database.entity.StockOperationEntity
import com.example.lcsc_android_erp.core.database.model.ContainerSlotStockProjection
import com.example.lcsc_android_erp.domain.model.ContainerSlot
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.QuantityState
import com.example.lcsc_android_erp.domain.model.SlotStockItem
import com.example.lcsc_android_erp.domain.model.StockOperation
import com.example.lcsc_android_erp.domain.repository.StockPlacementRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class StockPlacementRepositoryImpl(
    private val containerDao: ContainerDao,
    private val stockItemDao: StockItemDao,
    private val stockOperationDao: StockOperationDao
) : StockPlacementRepository {
    override fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>> {
        return containerDao.observeSlotStock(containerId).map { rows ->
            rows.map(::toContainerSlotStock)
        }
    }

    override fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>> {
        return containerDao.observeSlotStock(containerId).map { rows ->
            rows.mapNotNull(::toSlotStockItem)
        }
    }

    override suspend fun upsertStock(write: StockPlacementWrite): Long? {
        val existing = stockItemDao.findByComponentAndSlot(write.componentId, write.slotId)
        if (existing == null) {
            val insertedId = stockItemDao.insert(write.toEntity())
            return if (insertedId > 0) insertedId else null
        }
        val updated = existing.copy(
            containerId = write.containerId,
            quantity = write.quantity,
            quantityState = write.quantityState.name,
            safetyStockThreshold = write.safetyStockThreshold,
            lastInboundAt = write.lastInboundAt,
            updatedAt = write.updatedAt
        )
        stockItemDao.update(updated)
        return existing.id
    }

    override suspend fun replaceSlotStock(write: StockPlacementWrite): Long? {
        stockItemDao.deleteBySlotId(write.slotId)
        val insertedId = stockItemDao.insert(write.toEntity())
        return if (insertedId > 0) insertedId else null
    }

    override suspend fun deleteSlotStock(slotId: Long) {
        stockItemDao.deleteBySlotId(slotId)
    }

    override suspend fun deleteComponentFromSlot(componentId: Long, slotId: Long) {
        stockItemDao.deleteByComponentAndSlot(componentId, slotId)
    }

    override suspend fun deleteContainerStock(containerId: Long) {
        stockItemDao.deleteByContainerId(containerId)
    }

    override suspend fun recordOperation(operation: StockOperation): Long {
        return stockOperationDao.insert(
            StockOperationEntity(
                type = operation.type.name,
                containerId = operation.containerId,
                containerSlotId = operation.slotId,
                componentId = operation.componentId,
                quantityDelta = operation.quantityDelta,
                sourceType = operation.sourceType,
                sourceRef = operation.sourceRef,
                rawPayload = operation.rawPayload,
                bleOpcode = operation.bleOpcode,
                bleStatus = operation.bleStatus,
                tableSeqBefore = operation.tableSeqBefore,
                tableSeqAfter = operation.tableSeqAfter,
                createdAt = operation.createdAt
            )
        )
    }

    private fun StockPlacementWrite.toEntity(): StockItemEntity {
        return StockItemEntity(
            componentId = componentId,
            containerId = containerId,
            containerSlotId = slotId,
            quantity = quantity,
            quantityState = quantityState.name,
            safetyStockThreshold = safetyStockThreshold,
            lastInboundAt = lastInboundAt,
            updatedAt = updatedAt
        )
    }

    private fun toContainerSlotStock(projection: ContainerSlotStockProjection): ContainerSlotStock {
        val slot = ContainerSlot(
            id = projection.slotId,
            containerId = projection.containerId,
            containerCode = projection.containerCode,
            containerType = projection.containerType.toContainerType(),
            slotNumber = projection.slotNumber,
            slotCode = projection.slotCode,
            displayName = projection.slotDisplayName,
            sortOrder = projection.sortOrder
        )
        return ContainerSlotStock(
            slot = slot,
            stockItem = toSlotStockItem(projection)
        )
    }

    private fun toSlotStockItem(projection: ContainerSlotStockProjection): SlotStockItem? {
        val stockItemId = projection.stockItemId ?: return null
        val componentId = projection.componentId ?: return null
        val partNumber = projection.partNumber ?: return null
        val quantity = projection.quantity ?: return null
        return SlotStockItem(
            id = stockItemId,
            componentId = componentId,
            containerId = projection.containerId,
            slotId = projection.slotId,
            slotNumber = projection.slotNumber,
            partNumber = partNumber,
            protocolPartId = projection.protocolPartId ?: partNumber,
            quantity = quantity,
            quantityState = projection.quantityState.toQuantityState(),
            safetyStockThreshold = projection.safetyStockThreshold,
            mpn = projection.mpn,
            name = projection.name,
            brand = projection.brand,
            packageName = projection.packageName,
            category = projection.category,
            description = projection.description,
            sourceUrl = projection.sourceUrl,
            specifications = parseSpecifications(projection.specJson),
            imageLocalPath = projection.imageLocalPath,
            updatedAt = projection.updatedAt ?: 0
        )
    }

    private fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }

    private fun String?.toQuantityState(): QuantityState {
        return this
            ?.let { value -> runCatching { QuantityState.valueOf(value) }.getOrNull() }
            ?: QuantityState.KNOWN
    }

    private fun parseSpecifications(specJson: String?): Map<String, String> {
        if (specJson.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            val json = JSONObject(specJson)
            json.keys().asSequence().associateWith { key ->
                json.optString(key)
            }.filterValues { value ->
                value.isNotBlank() && value != "null"
            }
        }.getOrDefault(emptyMap())
    }
}
