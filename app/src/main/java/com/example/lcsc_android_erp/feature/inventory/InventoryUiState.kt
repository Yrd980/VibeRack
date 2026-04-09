package com.example.lcsc_android_erp.feature.inventory

import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocation
import com.example.lcsc_android_erp.domain.model.ComponentDetail

data class InventoryUiState(
    val cells: List<StockLocationCell> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocation: StockLocationCell? = null,
    val selectedLocationItems: List<LocationInventoryItem> = emptyList(),
    val pendingOpenRequest: InventoryOpenRequest? = null,
    val settingsLocationSortAttributes: List<String> = emptyList(),
    val addMaterialSearchResults: List<ComponentDetail> = emptyList(),
    val isSearchingAddMaterial: Boolean = false,
    val addMaterialSearchError: String? = null,
    val addLocationError: String? = null,
    val updateLocationError: String? = null
)
