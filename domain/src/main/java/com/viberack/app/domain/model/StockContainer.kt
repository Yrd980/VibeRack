package com.viberack.app.domain.model

data class StockContainer(
    val id: Long,
    val code: String,
    val displayName: String?,
    val type: ContainerType,
    val slotCount: Int,
    val colorHex: String? = null,
    val sortMode: String = "",
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
) {
    val isSmartChassisCachePossiblyStale: Boolean
        get() = type == ContainerType.SMART_CHASSIS &&
            tableSeq != null &&
            tableCrc16 != null &&
            lastSyncedAt == null
}
