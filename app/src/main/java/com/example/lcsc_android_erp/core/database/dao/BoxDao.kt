package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.BoxEntity
import com.example.lcsc_android_erp.core.database.entity.BoxLayerEntity
import com.example.lcsc_android_erp.core.database.entity.LayerMaterialEntity
import com.example.lcsc_android_erp.core.database.model.BoxLayerProjection
import com.example.lcsc_android_erp.core.database.model.BoxSummaryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface BoxDao {
    @Query(
        """
        SELECT
            b.id AS id,
            b.code AS code,
            b.name AS name,
            b.layerCount AS layerCount,
            CAST(COUNT(lm.id) AS INTEGER) AS occupiedLayerCount
        FROM component_box b
        LEFT JOIN box_layer bl ON bl.box_id = b.id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        GROUP BY b.id
        ORDER BY b.code ASC
        """
    )
    fun observeBoxSummaries(): Flow<List<BoxSummaryProjection>>

    @Query("SELECT * FROM component_box WHERE code = :code LIMIT 1")
    suspend fun findBoxByCode(code: String): BoxEntity?

    @Query("SELECT * FROM component_box WHERE id = :boxId LIMIT 1")
    suspend fun findBoxById(boxId: Long): BoxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBox(box: BoxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLayers(layers: List<BoxLayerEntity>)

    @Update
    suspend fun updateBox(box: BoxEntity)

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE bl.box_id = :boxId
        ORDER BY bl.sortOrder ASC, bl.layer_code ASC
        """
    )
    fun observeLayersForBox(boxId: Long): Flow<List<BoxLayerProjection>>

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        ORDER BY b.code ASC, bl.sortOrder ASC, bl.layer_code ASC
        """
    )
    fun observeAllLayers(): Flow<List<BoxLayerProjection>>

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE lm.id IS NULL
        ORDER BY b.code ASC, bl.sortOrder ASC, bl.layer_code ASC
        """
    )
    fun observeEmptyLayers(): Flow<List<BoxLayerProjection>>

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE bl.id = :layerId
        LIMIT 1
        """
    )
    suspend fun findLayerById(layerId: Long): BoxLayerProjection?

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE UPPER(b.code) = UPPER(:boxCode)
            AND UPPER(bl.layer_code) = UPPER(:layerCode)
        LIMIT 1
        """
    )
    suspend fun findLayerByPosition(boxCode: String, layerCode: String): BoxLayerProjection?

    @Query(
        """
        SELECT
            bl.id AS id,
            bl.box_id AS boxId,
            b.code AS boxCode,
            bl.layer_code AS layerCode,
            bl.displayName AS displayName,
            bl.sortOrder AS sortOrder,
            c.id AS componentId,
            c.part_number AS partNumber,
            c.name AS componentName,
            lm.quantity AS quantity
        FROM box_layer bl
        INNER JOIN component_box b ON b.id = bl.box_id
        LEFT JOIN layer_material lm ON lm.layer_id = bl.id
        LEFT JOIN component_master c ON c.id = lm.component_id
        WHERE lm.id IS NULL
        ORDER BY b.code ASC, bl.sortOrder ASC, bl.layer_code ASC
        LIMIT 1
        """
    )
    suspend fun findFirstEmptyLayer(): BoxLayerProjection?

    @Query("SELECT COUNT(*) FROM box_layer WHERE box_id = :boxId")
    suspend fun countLayersForBox(boxId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceLayerMaterial(layerMaterial: LayerMaterialEntity)
}
