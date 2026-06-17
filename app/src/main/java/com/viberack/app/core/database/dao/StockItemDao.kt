package com.viberack.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.model.SearchInventoryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface StockItemDao {
    @Query("SELECT * FROM stock_item ORDER BY id ASC")
    suspend fun getAll(): List<StockItemEntity>

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

    @Query(
        """
        SELECT * FROM stock_item
        WHERE container_id = :containerId
        ORDER BY id ASC
        """
    )
    suspend fun getByContainerId(containerId: Long): List<StockItemEntity>

    @Query(
        """
        SELECT
            si.id AS inventoryItemId,
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
        ORDER BY cm.part_number ASC, c.type ASC, c.code ASC, cs.slot_number ASC
        """
    )
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryProjection>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: StockItemEntity): Long

    @Update
    suspend fun update(item: StockItemEntity)

    @Query("DELETE FROM stock_item WHERE id = :stockItemId")
    suspend fun deleteById(stockItemId: Long)

    @Query("DELETE FROM stock_item WHERE container_slot_id = :containerSlotId")
    suspend fun deleteBySlotId(containerSlotId: Long)

    @Query(
        """
        DELETE FROM stock_item
        WHERE component_id = :componentId AND container_slot_id = :containerSlotId
        """
    )
    suspend fun deleteByComponentAndSlot(componentId: Long, containerSlotId: Long)

    @Query("DELETE FROM stock_item WHERE container_id = :containerId")
    suspend fun deleteByContainerId(containerId: Long)

    @Query("DELETE FROM stock_item")
    suspend fun deleteAll()
}
