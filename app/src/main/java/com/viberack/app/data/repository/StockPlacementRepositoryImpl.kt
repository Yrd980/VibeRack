package com.viberack.app.data.repository

import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity
import com.viberack.app.core.database.model.ContainerSlotStockProjection
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.SlotStockItem
import com.viberack.app.domain.model.StockOperation
import com.viberack.app.domain.repository.StockPlacementRepository
import com.viberack.app.domain.repository.StockPlacementWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    override suspend fun getContainerSlotStock(containerId: Long): List<ContainerSlotStock> {
        return containerDao.getSlotStock(containerId).map(::toContainerSlotStock)
    }

    override suspend fun findSlotStock(slotId: Long): ContainerSlotStock? {
        return containerDao.findSlotStock(slotId)?.let(::toContainerSlotStock)
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

    override suspend fun updateStockQuantity(stockItemId: Long, quantity: Int, updatedAt: Long) {
        val existing = stockItemDao.findById(stockItemId) ?: return
        stockItemDao.update(
            existing.copy(
                quantity = quantity,
                quantityState = QuantityState.KNOWN.name,
                updatedAt = updatedAt
            )
        )
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
        return ContainerReadModels.containerSlotStock(projection)
    }

    private fun toSlotStockItem(projection: ContainerSlotStockProjection): SlotStockItem? {
        return ContainerReadModels.slotStockItem(projection)
    }
}
