package com.example.lcsc_android_erp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "component_master",
    indices = [
        Index(value = ["part_number"], unique = true),
        Index(value = ["protocol_part_id"], unique = true),
        Index(value = ["mpn"]),
        Index(value = ["brand"]),
        Index(value = ["package_name"])
    ]
)
data class ComponentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "part_number")
    val partNumber: String,
    @ColumnInfo(name = "protocol_part_id")
    val protocolPartId: String? = null,
    val mpn: String? = null,
    val name: String? = null,
    val brand: String? = null,
    @ColumnInfo(name = "package_name")
    val packageName: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "spec_json")
    val specJson: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "image_local_path")
    val imageLocalPath: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
