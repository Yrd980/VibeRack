package com.viberack.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.viberack.app.core.database.dao.BoxDao
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.dao.InventoryTransactionDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.dao.StorageLocationDao
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.calculateDominantLocationCategoryProfile

internal class InventoryBackupRestorer(
    private val database: RoomDatabase,
    private val boxDao: BoxDao,
    private val storageLocationDao: StorageLocationDao,
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val containerDao: ContainerDao,
    private val stockItemDao: StockItemDao,
    private val stockOperationDao: StockOperationDao
) {
    suspend fun restore(parsedBackup: InventoryBackupParser.ParsedBackup) {
        val components = parsedBackup.importedComponents.map { it.entity }
        database.withTransaction {
            stockOperationDao.deleteAll()
            stockItemDao.deleteAll()
            containerDao.getAllContainers().forEach { container ->
                containerDao.deleteContainerById(container.id)
            }
            boxDao.deleteAllBoxes()
            inventoryTransactionDao.deleteAll()
            inventoryItemDao.deleteAll()
            componentDao.deleteAll()
            storageLocationDao.deleteAll()

            if (parsedBackup.storageLocations.isNotEmpty()) {
                storageLocationDao.insertAll(parsedBackup.storageLocations)
            }
            if (components.isNotEmpty()) {
                componentDao.insertAll(components)
            }
            if (parsedBackup.inventoryItems.isNotEmpty()) {
                inventoryItemDao.insertAll(parsedBackup.inventoryItems)
            }
            if (parsedBackup.boxes.isNotEmpty()) {
                parsedBackup.boxes.forEach { box -> boxDao.insertBox(box) }
            }
            if (parsedBackup.boxLayers.isNotEmpty()) {
                boxDao.insertLayers(parsedBackup.boxLayers)
            }
            if (parsedBackup.containers.isNotEmpty() && parsedBackup.containerSlots.isNotEmpty()) {
                parsedBackup.containers.forEach { container -> containerDao.insertContainer(container) }
                containerDao.insertSlots(parsedBackup.containerSlots)
                parsedBackup.stockItems.forEach { stockItem -> stockItemDao.insert(stockItem) }
                parsedBackup.stockOperations.forEach { operation -> stockOperationDao.insert(operation) }
            } else {
                rebuildLegacyContainerStock(parsedBackup.storageLocations, parsedBackup.inventoryItems)
            }
            refreshAllLocationCategoryProfiles()
        }
    }

    private suspend fun refreshAllLocationCategoryProfiles() {
        val profilesByLocation = inventoryItemDao.getAllLocationCategoryProfiles()
            .map { projection ->
                LocationCategoryProfile(
                    locationId = projection.locationId,
                    category = projection.category,
                    packageName = projection.packageName,
                    quantity = projection.quantity
                )
            }
            .groupBy(LocationCategoryProfile::locationId)
        storageLocationDao.getAll().forEach { location ->
            val profile = calculateDominantLocationCategoryProfile(profilesByLocation[location.id].orEmpty())
            storageLocationDao.updateInboundProfile(
                locationId = location.id,
                category = profile.category,
                packageName = profile.packageName,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun rebuildLegacyContainerStock(
        locations: List<StorageLocationEntity>,
        inventoryItems: List<InventoryItemEntity>
    ) {
        val slotsByLocationId = mutableMapOf<Long, ContainerSlotEntity>()
        locations.forEach { location ->
            containerDao.insertContainer(
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
                    updatedAt = location.createdAt
                )
            )
            val slot = ContainerSlotEntity(
                containerId = location.id,
                slotNumber = 1,
                slotCode = location.code,
                displayName = location.displayName ?: location.code,
                sortOrder = 1,
                createdAt = location.createdAt,
                updatedAt = location.createdAt
            )
            val slotId = containerDao.insertSlot(slot)
            slotsByLocationId[location.id] =
                containerDao.findSlotByContainerAndNumber(location.id, 1) ?: slot.copy(id = slotId)
        }
        inventoryItems.forEach { item ->
            val slot = slotsByLocationId[item.locationId] ?: return@forEach
            stockItemDao.insert(
                StockItemEntity(
                    componentId = item.componentId,
                    containerId = item.locationId,
                    containerSlotId = slot.id,
                    quantity = item.quantity,
                    quantityState = QuantityState.KNOWN.name,
                    lastInboundAt = item.lastInboundAt,
                    updatedAt = item.updatedAt
                )
            )
        }
    }
}
