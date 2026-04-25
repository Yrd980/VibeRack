package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lcsc_android_erp.core.database.entity.InventoryTransactionEntity

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_txn ORDER BY id ASC")
    suspend fun getAll(): List<InventoryTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: InventoryTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<InventoryTransactionEntity>)

    @Query(
        """
        SELECT source_ref FROM inventory_txn
        WHERE source_type = 'MANUAL_INPUT'
          AND source_ref GLOB 'C[0-9][0-9]*'
        ORDER BY id ASC
        """
    )
    suspend fun getManualInboundSourceRefs(): List<String>

    @Query(
        """
        DELETE FROM inventory_txn
        WHERE location_id = :locationId
        """
    )
    suspend fun deleteByLocationId(locationId: Long)

    @Query("DELETE FROM inventory_txn")
    suspend fun deleteAll()
}
