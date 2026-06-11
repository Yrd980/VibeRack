package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity
import com.example.lcsc_android_erp.core.database.model.StorageLocationSummaryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageLocationDao {
    @Query("SELECT * FROM storage_location ORDER BY code ASC")
    suspend fun getAll(): List<StorageLocationEntity>

    @Query("SELECT * FROM storage_location ORDER BY code ASC")
    fun observeAll(): Flow<List<StorageLocationEntity>>

    @Query("SELECT COUNT(*) FROM storage_location")
    suspend fun count(): Int

    @Query("SELECT * FROM storage_location WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): StorageLocationEntity?

    @Query("SELECT * FROM storage_location WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): StorageLocationEntity?

    @Query(
        """
        SELECT
            sl.id AS id,
            sl.code AS code,
            sl.displayName AS displayName,
            sl.colorHex AS colorHex,
            sl.sortMode AS sortMode,
            sl.remark AS remark,
            CAST(COUNT(si.id) AS INTEGER) AS inventoryItemCount,
            CAST(COALESCE(SUM(si.quantity), 0) AS INTEGER) AS totalQuantity
        FROM storage_location sl
        LEFT JOIN `container` c
            ON c.id = sl.id
            AND c.type = 'LEGACY_LOCATION'
        LEFT JOIN stock_item si ON si.container_id = c.id
        GROUP BY sl.id
        ORDER BY sl.code ASC
        """
    )
    fun observeLocationSummaries(): Flow<List<StorageLocationSummaryProjection>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(locations: List<StorageLocationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: StorageLocationEntity): Long

    @Update
    suspend fun update(location: StorageLocationEntity)

    @Query(
        """
        UPDATE storage_location
        SET inbound_category = :category,
            inbound_package_name = :packageName,
            inbound_profile_updated_at = :updatedAt
        WHERE id = :locationId
        """
    )
    suspend fun updateInboundProfile(
        locationId: Long,
        category: String?,
        packageName: String?,
        updatedAt: Long
    )

    @Query(
        """
        SELECT * FROM storage_location
        WHERE inbound_profile_updated_at = 0
        ORDER BY code ASC
        """
    )
    suspend fun findLocationsMissingInboundProfile(): List<StorageLocationEntity>

    @Query("DELETE FROM storage_location WHERE id = :locationId")
    suspend fun deleteById(locationId: Long)

    @Query("DELETE FROM storage_location")
    suspend fun deleteAll()
}
