package com.viberack.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.viberack.app.core.database.dao.BoxDao
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.DashboardDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.dao.InventoryTransactionDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.dao.StorageLocationDao
import com.viberack.app.core.database.entity.BoxEntity
import com.viberack.app.core.database.entity.BoxLayerEntity
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.InventoryTransactionEntity
import com.viberack.app.core.database.entity.LayerMaterialEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity
import com.viberack.app.core.database.entity.StorageLocationEntity

@Database(
    entities = [
        ComponentEntity::class,
        StorageLocationEntity::class,
        InventoryItemEntity::class,
        InventoryTransactionEntity::class,
        BoxEntity::class,
        BoxLayerEntity::class,
        LayerMaterialEntity::class,
        ContainerEntity::class,
        ContainerSlotEntity::class,
        StockItemEntity::class,
        StockOperationEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun componentDao(): ComponentDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun boxDao(): BoxDao
    abstract fun containerDao(): ContainerDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun stockOperationDao(): StockOperationDao
}
