package com.viberack.app.feature.inventory

import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.domain.model.ComponentDetail

data class InventoryUiState(
    val cells: List<StockLocationCell> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocation: StockLocationCell? = null,
    val selectedLocationItems: List<LocationInventoryItem> = emptyList(),
    val pendingOpenRequest: InventoryOpenRequest? = null,
    val settingsLocationSortAttributes: List<String> = emptyList(),
    val recentLocationColors: List<String> = emptyList(),
    val addMaterialSearchResults: List<ComponentDetail> = emptyList(),
    val isSearchingAddMaterial: Boolean = false,
    val addMaterialSearchError: String? = null,
    val addLocationError: String? = null,
    val updateLocationError: String? = null
)
