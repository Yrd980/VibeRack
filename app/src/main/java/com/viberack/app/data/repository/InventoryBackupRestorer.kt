package com.viberack.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao

internal class InventoryBackupRestorer(
    private val database: RoomDatabase,
    private val componentDao: ComponentDao,
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
            componentDao.deleteAll()

            if (components.isNotEmpty()) {
                componentDao.insertAll(components)
            }
            parsedBackup.containers.forEach { container -> containerDao.insertContainer(container) }
            containerDao.insertSlots(parsedBackup.containerSlots)
            parsedBackup.stockItems.forEach { stockItem -> stockItemDao.insert(stockItem) }
            parsedBackup.stockOperations.forEach { operation -> stockOperationDao.insert(operation) }
        }
    }
}
