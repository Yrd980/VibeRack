package com.viberack.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "container_slot",
    foreignKeys = [
        ForeignKey(
            entity = ContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["container_id"]),
        Index(value = ["container_id", "slot_number"], unique = true),
        Index(value = ["container_id", "slot_code"], unique = true)
    ]
)
data class ContainerSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "container_id")
    val containerId: Long,
    @ColumnInfo(name = "slot_number")
    val slotNumber: Int,
    @ColumnInfo(name = "slot_code")
    val slotCode: String,
    val displayName: String? = null,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
