package com.viberack.app.domain.model

data class SlotStockItem(
    val id: Long,
    val componentId: Long,
    val containerId: Long,
    val slotId: Long,
    val slotNumber: Int,
    val partNumber: String,
    val protocolPartId: String,
    val quantity: Int,
    val quantityState: QuantityState = QuantityState.KNOWN,
    val safetyStockThreshold: Int? = null,
    val mpn: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val description: String? = null,
    val sourceUrl: String? = null,
    val specifications: Map<String, String> = emptyMap(),
    val imageLocalPath: String? = null,
    val updatedAt: Long = 0
) {
    val isAtOrBelowSafetyStock: Boolean
        get() = quantityState == QuantityState.KNOWN &&
            safetyStockThreshold != null &&
            quantity <= safetyStockThreshold

    val isBelowSafetyStock: Boolean
        get() = isAtOrBelowSafetyStock
}
