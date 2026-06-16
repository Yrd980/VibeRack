package com.viberack.app.domain.model

data class SearchInventoryRecord(
    val inventoryItemId: Long,
    val stockItemId: Long? = null,
    val isLegacyEditable: Boolean = true,
    val componentId: Long,
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val sourceUrl: String? = null,
    val specifications: Map<String, String> = emptyMap(),
    val imageLocalPath: String? = null,
    val quantity: Int,
    val locationId: Long,
    val locationCode: String,
    val locationDisplayName: String?,
    val locationColorHex: String?,
    val containerType: ContainerType = ContainerType.LEGACY_LOCATION,
    val containerMacAddress: String? = null,
    val slotId: Long? = null,
    val slotNumber: Int? = null,
    val slotCode: String? = null,
    val slotDisplayName: String? = null
) {
    val canFindByLight: Boolean
        get() = containerType == ContainerType.SMART_CHASSIS &&
            !containerMacAddress.isNullOrBlank() &&
            slotNumber in 1..25
}
