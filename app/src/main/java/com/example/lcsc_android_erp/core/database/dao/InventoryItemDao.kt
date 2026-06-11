package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.model.ExistingStockLocationProjection
import com.example.lcsc_android_erp.core.database.model.LocationCategoryProfileProjection
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

    @Query("SELECT COUNT(*) FROM inventory_item WHERE component_id = :componentId")
    suspend fun countByComponent(componentId: Long): Int

    @Query("SELECT DISTINCT location_id FROM inventory_item WHERE component_id = :componentId")
    suspend fun getLocationIdsByComponent(componentId: Long): List<Long>

    @Query(
        """
        SELECT DISTINCT si.container_id
        FROM stock_item si
        INNER JOIN `container` c ON c.id = si.container_id
        WHERE si.component_id = :componentId
            AND c.type = 'LEGACY_LOCATION'
        """
    )
    suspend fun getLegacyLocationIdsByComponentFromStock(componentId: Long): List<Long>

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
            si.quantity AS quantity
        FROM stock_item si
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        INNER JOIN storage_location sl ON sl.id = c.id
        INNER JOIN inventory_item ii
            ON ii.component_id = si.component_id
            AND ii.location_id = sl.id
        WHERE cm.part_number = :partNumber
        ORDER BY sl.code ASC
        """
    )
    suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocationProjection>

    @Query(
        """
        SELECT DISTINCT cm.part_number
        FROM stock_item si
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        WHERE cm.part_number LIKE 'C0%'
        ORDER BY cm.part_number ASC
        """
    )
    suspend fun getInStockC0PrefixedPartNumbers(): List<String>

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
            si.quantity AS quantity,
            si.last_inbound_at AS lastInboundAt
        FROM stock_item si
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        INNER JOIN storage_location sl ON sl.id = c.id
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN inventory_item ii
            ON ii.component_id = si.component_id
            AND ii.location_id = sl.id
        WHERE c.id = :locationId
        ORDER BY cm.part_number ASC
        """
    )
    fun observeItemsByLocation(locationId: Long): Flow<List<LocationInventoryProjection>>

    @Query(
        """
        SELECT
            COALESCE(ii.id, si.id) AS inventoryItemId,
            ii.id AS legacyInventoryItemId,
            si.id AS stockItemId,
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
            si.quantity AS quantity,
            c.id AS locationId,
            c.code AS locationCode,
            CASE
                WHEN c.type = 'SMART_CHASSIS' THEN COALESCE(cs.displayName, 'Slot ' || cs.slot_number)
                WHEN c.type = 'BOX' THEN COALESCE(cs.displayName, cs.slot_code)
                ELSE c.displayName
            END AS locationDisplayName,
            c.colorHex AS locationColorHex,
            c.type AS containerType,
            c.macAddress AS containerMacAddress,
            cs.id AS slotId,
            cs.slot_number AS slotNumber,
            cs.slot_code AS slotCode,
            cs.displayName AS slotDisplayName
        FROM stock_item si
        INNER JOIN `container` c ON c.id = si.container_id
        INNER JOIN container_slot cs ON cs.id = si.container_slot_id
        INNER JOIN component_master cm ON cm.id = si.component_id
        LEFT JOIN inventory_item ii
            ON ii.component_id = si.component_id
            AND ii.location_id = c.id
            AND c.type = 'LEGACY_LOCATION'
        ORDER BY cm.part_number ASC, c.type ASC, c.code ASC, cs.slot_number ASC
        """
    )
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryProjection>>

    @Query(
        """
        SELECT
            si.container_id AS locationId,
            cm.category AS category,
            cm.package_name AS packageName,
            si.quantity AS quantity
        FROM stock_item si
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        """
    )
    fun observeLocationCategoryProfiles(): Flow<List<LocationCategoryProfileProjection>>

    @Query(
        """
        SELECT
            si.container_id AS locationId,
            cm.category AS category,
            cm.package_name AS packageName,
            si.quantity AS quantity
        FROM stock_item si
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        WHERE si.container_id = :locationId
        """
    )
    suspend fun getLocationCategoryProfiles(locationId: Long): List<LocationCategoryProfileProjection>

    @Query(
        """
        SELECT
            si.container_id AS locationId,
            cm.category AS category,
            cm.package_name AS packageName,
            si.quantity AS quantity
        FROM stock_item si
        INNER JOIN component_master cm ON cm.id = si.component_id
        INNER JOIN `container` c
            ON c.id = si.container_id
            AND c.type = 'LEGACY_LOCATION'
        """
    )
    suspend fun getAllLocationCategoryProfiles(): List<LocationCategoryProfileProjection>

    @Query("DELETE FROM inventory_item")
    suspend fun deleteAll()
}
