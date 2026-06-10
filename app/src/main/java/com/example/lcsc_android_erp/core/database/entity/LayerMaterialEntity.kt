package com.example.lcsc_android_erp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "layer_material",
    foreignKeys = [
        ForeignKey(
            entity = BoxLayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["layer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ComponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["layer_id"], unique = true),
        Index(value = ["component_id"])
    ]
)
data class LayerMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "layer_id")
    val layerId: Long,
    @ColumnInfo(name = "component_id")
    val componentId: Long,
    val quantity: Int = 0,
    val sourceType: String? = null,
    val rawPayload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
