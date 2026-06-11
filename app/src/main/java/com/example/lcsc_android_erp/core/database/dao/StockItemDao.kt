package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.StockItemEntity

@Dao
interface StockItemDao {
    @Query("SELECT * FROM stock_item WHERE id = :stockItemId LIMIT 1")
    suspend fun findById(stockItemId: Long): StockItemEntity?

    @Query(
        """
        SELECT * FROM stock_item
        WHERE component_id = :componentId AND container_slot_id = :containerSlotId
        LIMIT 1
        """
    )
    suspend fun findByComponentAndSlot(componentId: Long, containerSlotId: Long): StockItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: StockItemEntity): Long

    @Update
    suspend fun update(item: StockItemEntity)

    @Query("DELETE FROM stock_item WHERE id = :stockItemId")
    suspend fun deleteById(stockItemId: Long)
}
