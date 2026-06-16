package com.viberack.app.feature.inventory

import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation

data class InventoryScanLookupResult(
    val component: ComponentDetail? = null,
    val quantity: Int = 0,
    val rawPayload: String? = null,
    val errorMessage: String? = null,
    val existingStockLocations: List<ExistingStockLocation> = emptyList()
)
