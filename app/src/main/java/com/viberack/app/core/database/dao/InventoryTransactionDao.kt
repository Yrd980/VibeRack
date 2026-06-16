package com.viberack.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.viberack.app.core.database.entity.InventoryTransactionEntity

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
        DELETE FROM inventory_txn
        WHERE location_id = :locationId
        """
    )
    suspend fun deleteByLocationId(locationId: Long)

    @Query("DELETE FROM inventory_txn")
    suspend fun deleteAll()
}
