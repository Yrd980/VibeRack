package com.viberack.app.domain.model

data class InboundRecord(
    val component: ComponentDetail,
    val quantity: Int,
    val locationCode: String,
    val sourceType: String,
    val rawPayload: String? = null,
    val inboundAt: Long = System.currentTimeMillis()
)
