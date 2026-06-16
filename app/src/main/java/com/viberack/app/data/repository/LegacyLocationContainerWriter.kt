package com.viberack.app.data.repository

import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.repository.StockPlacementRepository
import com.viberack.app.domain.repository.StockPlacementWrite

internal class LegacyLocationContainerWriter(
    private val containerDao: ContainerDao,
    private val stockPlacementRepository: StockPlacementRepository
) {
    suspend fun ensureContainer(location: StorageLocationEntity): ContainerSlotEntity {
        val now = System.currentTimeMillis()
        val container = containerDao.findContainerById(location.id)
        if (container == null) {
            val insertId = containerDao.insertContainer(
                ContainerEntity(
                    id = location.id,
                    code = location.code,
                    displayName = location.displayName,
                    type = ContainerType.LEGACY_LOCATION.name,
                    slotCount = 1,
                    colorHex = location.colorHex,
                    sortMode = location.sortMode,
                    remark = location.remark,
                    createdAt = location.createdAt,
                    updatedAt = now
                )
            )
            if (insertId <= 0) {
                containerDao.findContainerById(location.id)?.let { existing ->
                    containerDao.updateContainer(existing.toLegacyContainer(location, now))
                }
            }
        } else {
            containerDao.updateContainer(container.toLegacyContainer(location, now))
        }

        val slot = containerDao.findSlotByContainerAndNumber(location.id, 1)
        if (slot != null) {
            val updatedSlot = slot.copy(
                slotCode = location.code,
                displayName = location.displayName ?: location.code,
                sortOrder = 1,
                updatedAt = now
            )
            if (updatedSlot != slot) {
                containerDao.updateSlot(updatedSlot)
            }
            return updatedSlot
        }

        val newSlot = ContainerSlotEntity(
            containerId = location.id,
            slotNumber = 1,
            slotCode = location.code,
            displayName = location.displayName ?: location.code,
            sortOrder = 1,
            createdAt = location.createdAt,
            updatedAt = now
        )
        val slotId = containerDao.insertSlot(newSlot)
        return containerDao.findSlotByContainerAndNumber(location.id, 1)
            ?: newSlot.copy(id = slotId)
    }

    suspend fun upsertStockItem(
        item: InventoryItemEntity,
        slot: ContainerSlotEntity,
        updatedAt: Long
    ) {
        stockPlacementRepository.upsertStock(
            StockPlacementWrite(
                componentId = item.componentId,
                containerId = item.locationId,
                slotId = slot.id,
                quantity = item.quantity,
                lastInboundAt = item.lastInboundAt,
                updatedAt = updatedAt
            )
        )
    }

    private fun ContainerEntity.toLegacyContainer(
        location: StorageLocationEntity,
        updatedAt: Long
    ): ContainerEntity {
        return copy(
            code = location.code,
            displayName = location.displayName,
            type = ContainerType.LEGACY_LOCATION.name,
            slotCount = 1,
            colorHex = location.colorHex,
            sortMode = location.sortMode,
            remark = location.remark,
            updatedAt = updatedAt
        )
    }
}
