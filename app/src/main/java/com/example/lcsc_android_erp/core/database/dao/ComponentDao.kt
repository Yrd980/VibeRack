package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity

@Dao
interface ComponentDao {
    @Query("SELECT * FROM component_master ORDER BY id ASC")
    suspend fun getAll(): List<ComponentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(component: ComponentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(components: List<ComponentEntity>)

    @Query("SELECT * FROM component_master WHERE id = :componentId LIMIT 1")
    suspend fun findById(componentId: Long): ComponentEntity?

    @Query("SELECT * FROM component_master WHERE part_number = :partNumber LIMIT 1")
    suspend fun findByPartNumber(partNumber: String): ComponentEntity?

    @Query("SELECT * FROM component_master WHERE protocol_part_id = :protocolPartId LIMIT 1")
    suspend fun findByProtocolPartId(protocolPartId: String): ComponentEntity?

    @Update
    suspend fun update(component: ComponentEntity)

    @Query("DELETE FROM component_master")
    suspend fun deleteAll()
}
