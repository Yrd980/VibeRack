package com.example.lcsc_android_erp.domain.model

data class StockContainer(
    val id: Long,
    val code: String,
    val displayName: String?,
    val type: ContainerType,
    val slotCount: Int,
    val colorHex: String? = null,
    val sortMode: String = StorageLocationSortMode.NONE,
    val remark: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
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
