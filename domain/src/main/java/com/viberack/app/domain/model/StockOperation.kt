package com.viberack.app.domain.model

enum class StockOperationType {
    INBOUND,
    ADJUST,
    TRANSFER_OUT,
    TRANSFER_IN,
    DELETE,
    WRITE_ONE,
    CLEAR_ONE,
    INSERT_AT,
    REMOVE_AT,
    MOVE_BLOCK,
    SET_QTY,
    READ_ALL_RESTORE,
    LIGHT_FIND,
    LIGHT_PICK,
    LIGHT_SORT,
    LIGHT_STOCK_IN
}

data class StockOperation(
    val id: Long = 0,
    val type: StockOperationType,
    val containerId: Long?,
    val slotId: Long?,
    val componentId: Long?,
    val quantityDelta: Int = 0,
    val sourceType: String? = null,
    val sourceRef: String? = null,
    val rawPayload: String? = null,
    val bleOpcode: Int? = null,
    val bleStatus: Int? = null,
    val tableSeqBefore: Long? = null,
    val tableSeqAfter: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
