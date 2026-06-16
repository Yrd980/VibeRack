package com.viberack.app.domain.model

data class ContainerSlot(
    val id: Long,
    val containerId: Long,
    val containerCode: String,
    val containerType: ContainerType,
    val slotNumber: Int,
    val slotCode: String,
    val displayName: String? = null,
    val sortOrder: Int = slotNumber
) {
    val positionCode: String
        get() = if (containerType == ContainerType.LEGACY_LOCATION && slotNumber == 1) {
            containerCode
        } else {
            "$containerCode-$slotCode"
        }
}
