package com.viberack.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.viberack.app.domain.model.StorageLocationSortMode

@Entity(
    tableName = "storage_location",
    indices = [Index(value = ["code"], unique = true)]
)
data class StorageLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val displayName: String? = null,
    val colorHex: String? = null,
    @ColumnInfo(defaultValue = "")
    val sortMode: String = StorageLocationSortMode.NONE,
    @ColumnInfo(name = "inbound_category")
    val inboundCategory: String? = null,
    @ColumnInfo(name = "inbound_package_name")
    val inboundPackageName: String? = null,
    @ColumnInfo(name = "inbound_profile_updated_at", defaultValue = "0")
    val inboundProfileUpdatedAt: Long = 0,
    val remark: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
