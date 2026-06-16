package com.viberack.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.viberack.app.core.database.entity.StockOperationEntity

@Dao
interface StockOperationDao {
    @Query("SELECT * FROM stock_operation ORDER BY id ASC")
    suspend fun getAll(): List<StockOperationEntity>

    @Insert
    suspend fun insert(operation: StockOperationEntity): Long

    @Query("DELETE FROM stock_operation")
    suspend fun deleteAll()
}
