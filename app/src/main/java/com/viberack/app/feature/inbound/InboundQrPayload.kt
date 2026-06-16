package com.viberack.app.feature.inbound

data class InboundQrPayload(
    val orderNo: String?,
    val partNumber: String,
    val manufacturerPartNo: String?,
    val quantity: Int,
    val rawText: String,
    val extraFields: Map<String, String>
)
