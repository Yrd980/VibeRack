package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.model.ContainerSlotStockProjection
import com.example.lcsc_android_erp.core.database.model.ContainerSummaryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
    @Query("SELECT * FROM `container` ORDER BY type ASC, code ASC")
    suspend fun getAllContainers(): List<ContainerEntity>

    @Query("SELECT * FROM container_slot ORDER BY container_id ASC, sortOrder ASC, slot_number ASC")
    suspend fun getAllSlots(): List<ContainerSlotEntity>

    @Query(
        """
        SELECT
            c.id AS id,
            c.code AS code,
            c.displayName AS displayName,
            c.type AS type,
            c.slotCount AS slotCount,
            c.colorHex AS colorHex,
            c.sortMode AS sortMode,
            c.remark AS remark,
            c.createdAt AS createdAt,
            c.updatedAt AS updatedAt,
            c.macAddress AS macAddress,
            c.batchId AS batchId,
            c.protoVersion AS protoVersion,
            c.firmwareVersion AS firmwareVersion,
            c.hardwareVersion AS hardwareVersion,
            c.batteryPct AS batteryPct,
            c.statusFlags AS statusFlags,
            c.tableSeq AS tableSeq,
            c.tableCrc16 AS tableCrc16,
            c.lastSeenAt AS lastSeenAt,
            c.lastSyncedAt AS lastSyncedAt,
            CAST(COUNT(DISTINCT si.container_slot_id) AS INTEGER) AS occupiedSlotCount,
            CAST(COALESCE(SUM(si.quantity), 0) AS INTEGER) AS totalQuantity
        FROM `container` c
        LEFT JOIN stock_item si ON si.container_id = c.id
        GROUP BY c.id
        ORDER BY c.type ASC, c.code ASC
        """
    )
    fun observeContainerSummaries(): Flow<List<ContainerSummaryProjection>>

    @Query("SELECT * FROM `container` WHERE id = :containerId LIMIT 1")
    suspend fun findContainerById(containerId: Long): ContainerEntity?

    @Query("SELECT * FROM `container` WHERE code = :code LIMIT 1")
    suspend fun findContainerByCode(code: String): ContainerEntity?

    @Query("SELECT * FROM `container` WHERE macAddress = :macAddress LIMIT 1")
    suspend fun findContainerByMacAddress(macAddress: String): ContainerEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContainer(container: ContainerEntity): Long

    @Update
    suspend fun updateContainer(container: ContainerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSlots(slots: List<ContainerSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSlot(slot: ContainerSlotEntity): Long

    @Update
    suspend fun updateSlot(slot: ContainerSlotEntity)

    @Query("SELECT * FROM container_slot WHERE container_id = :containerId ORDER BY sortOrder ASC, slot_number ASC")
    suspend fun getSlots(containerId: Long): List<ContainerSlotEntity>

    @Query(
        """
        SELECT * FROM container_slot
        WHERE container_id = :containerId AND slot_number = :slotNumber
        LIMIT 1
        """
    )
    suspend fun findSlotByContainerAndNumber(containerId: Long, slotNumber: Int): ContainerSlotEntity?

    @Query("DELETE FROM `container` WHERE id = :containerId")
    suspend fun deleteContainerById(containerId: Long)

    @Query("DELETE FROM `container` WHERE type = :type")
    suspend fun deleteContainersByType(type: String)

    @Query(
        """
        SELECT
            cs.id AS slotId,
            cs.container_id AS containerId,
            c.code AS containerCode,
            c.type AS containerType,
            cs.slot_number AS slotNumber,
            cs.slot_code AS slotCode,
            cs.displayName AS slotDisplayName,
            cs.sortOrder AS sortOrder,
            si.id AS stockItemId,
            cm.id AS componentId,
            cm.part_number AS partNumber,
            cm.protocol_part_id AS protocolPartId,
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
            si.quantity_state AS quantityState,
            si.safety_stock_threshold AS safetyStockThreshold,
            si.updated_at AS updatedAt
        FROM container_slot cs
        INNER JOIN `container` c ON c.id = cs.container_id
        LEFT JOIN stock_item si ON si.container_slot_id = cs.id
        LEFT JOIN component_master cm ON cm.id = si.component_id
        WHERE cs.container_id = :containerId
        ORDER BY cs.sortOrder ASC, cs.slot_number ASC
        """
    )
    fun observeSlotStock(containerId: Long): Flow<List<ContainerSlotStockProjection>>

    @Query(
        """
        SELECT
            cs.id AS slotId,
            cs.container_id AS containerId,
            c.code AS containerCode,
            c.type AS containerType,
            cs.slot_number AS slotNumber,
            cs.slot_code AS slotCode,
            cs.displayName AS slotDisplayName,
            cs.sortOrder AS sortOrder,
            si.id AS stockItemId,
            cm.id AS componentId,
            cm.part_number AS partNumber,
            cm.protocol_part_id AS protocolPartId,
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
            si.quantity_state AS quantityState,
            si.safety_stock_threshold AS safetyStockThreshold,
            si.updated_at AS updatedAt
        FROM container_slot cs
        INNER JOIN `container` c ON c.id = cs.container_id
        LEFT JOIN stock_item si ON si.container_slot_id = cs.id
        LEFT JOIN component_master cm ON cm.id = si.component_id
        WHERE cs.container_id = :containerId
        ORDER BY cs.sortOrder ASC, cs.slot_number ASC
        """
    )
    suspend fun getSlotStock(containerId: Long): List<ContainerSlotStockProjection>

    @Query(
        """
        SELECT
            cs.id AS slotId,
            cs.container_id AS containerId,
            c.code AS containerCode,
            c.type AS containerType,
            cs.slot_number AS slotNumber,
            cs.slot_code AS slotCode,
            cs.displayName AS slotDisplayName,
            cs.sortOrder AS sortOrder,
            si.id AS stockItemId,
            cm.id AS componentId,
            cm.part_number AS partNumber,
            cm.protocol_part_id AS protocolPartId,
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
            si.quantity_state AS quantityState,
            si.safety_stock_threshold AS safetyStockThreshold,
            si.updated_at AS updatedAt
        FROM container_slot cs
        INNER JOIN `container` c ON c.id = cs.container_id
        LEFT JOIN stock_item si ON si.container_slot_id = cs.id
        LEFT JOIN component_master cm ON cm.id = si.component_id
        WHERE cs.id = :slotId
        LIMIT 1
        """
    )
    suspend fun findSlotStock(slotId: Long): ContainerSlotStockProjection?
}
