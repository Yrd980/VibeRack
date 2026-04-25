package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.model.ExistingStockLocationProjection
import com.example.lcsc_android_erp.core.database.model.LocationInventoryProjection
import com.example.lcsc_android_erp.core.database.model.SearchInventoryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {
    @Query("SELECT COUNT(*) FROM inventory_item WHERE location_id = :locationId")
    suspend fun countByLocation(locationId: Long): Int

    @Query("SELECT * FROM inventory_item ORDER BY id ASC")
    suspend fun getAll(): List<InventoryItemEntity>

    @Query(
        """
        SELECT * FROM inventory_item
        WHERE id = :inventoryItemId
        LIMIT 1
        """
    )
    suspend fun findById(inventoryItemId: Long): InventoryItemEntity?

    @Query(
        """
        SELECT * FROM inventory_item
        WHERE component_id = :componentId AND location_id = :locationId
        LIMIT 1
        """
    )
    suspend fun findByComponentAndLocation(componentId: Long, locationId: Long): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Update
    suspend fun update(item: InventoryItemEntity)

    @Query(
        """
        DELETE FROM inventory_item
        WHERE id = :inventoryItemId
        """
    )
    suspend fun deleteById(inventoryItemId: Long)

    @Query(
        """
        DELETE FROM inventory_item
        WHERE location_id = :locationId
        """
    )
    suspend fun deleteByLocationId(locationId: Long)

    @Query(
        """
        SELECT
            sl.code AS locationCode,
            sl.displayName AS locationDisplayName,
            ii.quantity AS quantity
        FROM inventory_item ii
        INNER JOIN component_master cm ON cm.id = ii.component_id
        INNER JOIN storage_location sl ON sl.id = ii.location_id
        WHERE cm.part_number = :partNumber
        ORDER BY sl.code ASC
        """
    )
    suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocationProjection>

    @Query(
        """
        SELECT
            ii.id AS inventoryItemId,
            cm.id AS componentId,
            cm.part_number AS partNumber,
            cm.mpn AS mpn,
            cm.name AS name,
            cm.brand AS brand,
            cm.package_name AS packageName,
            cm.category AS category,
            cm.description AS description,
            cm.source_url AS sourceUrl,
            cm.spec_json AS specJson,
            cm.image_local_path AS imageLocalPath,
            ii.quantity AS quantity,
            ii.last_inbound_at AS lastInboundAt
        FROM inventory_item ii
        INNER JOIN component_master cm ON cm.id = ii.component_id
        WHERE ii.location_id = :locationId
        ORDER BY cm.part_number ASC
        """
    )
    fun observeItemsByLocation(locationId: Long): Flow<List<LocationInventoryProjection>>

    @Query(
        """
        SELECT
            ii.id AS inventoryItemId,
            cm.id AS componentId,
            cm.part_number AS partNumber,
            cm.mpn AS mpn,
            cm.name AS name,
            cm.brand AS brand,
            cm.package_name AS packageName,
            cm.category AS category,
            cm.description AS description,
            cm.source_url AS sourceUrl,
            cm.spec_json AS specJson,
            cm.image_local_path AS imageLocalPath,
            ii.quantity AS quantity,
            sl.id AS locationId,
            sl.code AS locationCode,
            sl.displayName AS locationDisplayName,
            sl.colorHex AS locationColorHex
        FROM inventory_item ii
        INNER JOIN component_master cm ON cm.id = ii.component_id
        INNER JOIN storage_location sl ON sl.id = ii.location_id
        ORDER BY cm.part_number ASC, sl.code ASC
        """
    )
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryProjection>>

    @Query("DELETE FROM inventory_item")
    suspend fun deleteAll()
}
