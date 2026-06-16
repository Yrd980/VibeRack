package com.viberack.app.domain.model

data class StorageLocation(
    val id: Long,
    val code: String,
    val displayName: String?,
    val colorHex: String?,
    val sortMode: String,
    val remark: String?
)
