package com.viberack.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_operation",
    foreignKeys = [
        ForeignKey(
            entity = ContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ContainerSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_slot_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ComponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["type"]),
        Index(value = ["container_id"]),
        Index(value = ["container_slot_id"]),
        Index(value = ["component_id"]),
        Index(value = ["created_at"])
    ]
)
data class StockOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "container_id")
    val containerId: Long? = null,
    @ColumnInfo(name = "container_slot_id")
    val containerSlotId: Long? = null,
    @ColumnInfo(name = "component_id")
    val componentId: Long? = null,
    @ColumnInfo(name = "quantity_delta", defaultValue = "0")
    val quantityDelta: Int = 0,
    val sourceType: String? = null,
    val sourceRef: String? = null,
    val rawPayload: String? = null,
    val bleOpcode: Int? = null,
    val bleStatus: Int? = null,
    val tableSeqBefore: Long? = null,
    val tableSeqAfter: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
