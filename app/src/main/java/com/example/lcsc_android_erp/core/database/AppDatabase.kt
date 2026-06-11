package com.example.lcsc_android_erp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lcsc_android_erp.core.database.dao.BoxDao
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.DashboardDao
import com.example.lcsc_android_erp.core.database.dao.InventoryItemDao
import com.example.lcsc_android_erp.core.database.dao.InventoryTransactionDao
import com.example.lcsc_android_erp.core.database.dao.StorageLocationDao
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryTransactionEntity
import com.example.lcsc_android_erp.core.database.entity.LayerMaterialEntity
import com.example.lcsc_android_erp.core.database.entity.StockItemEntity
import com.example.lcsc_android_erp.core.database.entity.StockOperationEntity
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity

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
}
