package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.lcsc_android_erp.core.database.entity.StockOperationEntity

@Dao
interface StockOperationDao {
    @Insert
    suspend fun insert(operation: StockOperationEntity): Long

    @Query("DELETE FROM stock_operation")
    suspend fun deleteAll()
}
