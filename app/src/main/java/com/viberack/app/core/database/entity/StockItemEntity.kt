package com.viberack.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viberack.app.domain.model.QuantityState

@Entity(
    tableName = "stock_item",
    foreignKeys = [
        ForeignKey(
            entity = ComponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContainerSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_slot_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["component_id"]),
        Index(value = ["container_id"]),
        Index(value = ["container_slot_id"]),
        Index(value = ["component_id", "container_slot_id"], unique = true)
    ]
)
data class StockItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "component_id")
    val componentId: Long,
    @ColumnInfo(name = "container_id")
    val containerId: Long,
    @ColumnInfo(name = "container_slot_id")
    val containerSlotId: Long,
    val quantity: Int,
    @ColumnInfo(name = "quantity_state", defaultValue = "'KNOWN'")
    val quantityState: String = QuantityState.KNOWN.name,
    @ColumnInfo(name = "safety_stock_threshold")
    val safetyStockThreshold: Int? = null,
    @ColumnInfo(name = "last_inbound_at")
    val lastInboundAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
