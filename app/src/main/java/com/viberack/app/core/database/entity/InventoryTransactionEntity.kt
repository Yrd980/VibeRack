package com.viberack.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_txn",
    foreignKeys = [
        ForeignKey(
            entity = ComponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["component_id"]),
        Index(value = ["location_id"]),
        Index(value = ["txn_type"]),
        Index(value = ["source_type"])
    ]
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "component_id")
    val componentId: Long,
    @ColumnInfo(name = "location_id")
    val locationId: Long,
    @ColumnInfo(name = "txn_type")
    val txnType: String,
    @ColumnInfo(name = "quantity_delta")
    val quantityDelta: Int,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_ref")
    val sourceRef: String? = null,
    @ColumnInfo(name = "raw_payload")
    val rawPayload: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
