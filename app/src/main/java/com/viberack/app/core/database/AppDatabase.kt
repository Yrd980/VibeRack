package com.viberack.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.DashboardDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity

@Database(
    entities = [
        ComponentEntity::class,
        ContainerEntity::class,
        ContainerSlotEntity::class,
        StockItemEntity::class,
        StockOperationEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun componentDao(): ComponentDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun containerDao(): ContainerDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun stockOperationDao(): StockOperationDao
}
