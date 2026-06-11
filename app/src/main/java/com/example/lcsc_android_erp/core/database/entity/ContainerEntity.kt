package com.example.lcsc_android_erp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode

@Entity(
    tableName = "container",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["macAddress"])
    ]
)
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val displayName: String? = null,
    val type: String = ContainerType.LEGACY_LOCATION.name,
    val slotCount: Int,
    val colorHex: String? = null,
    @ColumnInfo(defaultValue = "")
    val sortMode: String = StorageLocationSortMode.NONE,
    val remark: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val macAddress: String? = null,
    val batchId: Int? = null,
    val protoVersion: Int? = null,
    val firmwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val batteryPct: Int? = null,
    val statusFlags: Int? = null,
    val tableSeq: Long? = null,
    val tableCrc16: Int? = null,
    val lastSeenAt: Long? = null,
    val lastSyncedAt: Long? = null
)
