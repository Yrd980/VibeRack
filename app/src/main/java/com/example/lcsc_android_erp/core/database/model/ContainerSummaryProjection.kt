package com.example.lcsc_android_erp.core.database.model

data class ContainerSummaryProjection(
    val id: Long,
    val code: String,
    val displayName: String?,
    val type: String,
    val slotCount: Int,
    val colorHex: String?,
    val sortMode: String,
    val remark: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val macAddress: String?,
    val batchId: Int?,
    val protoVersion: Int?,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val batteryPct: Int?,
    val statusFlags: Int?,
    val tableSeq: Long?,
    val tableCrc16: Int?,
    val lastSeenAt: Long?,
    val lastSyncedAt: Long?,
    val occupiedSlotCount: Int,
    val totalQuantity: Int
)
