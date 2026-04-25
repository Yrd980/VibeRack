package com.example.lcsc_android_erp.feature.inbound

import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.StorageLocation

data class InboundUiState(
    val defaultLocationCode: String? = null,
    val nextManualInboundPartNumber: String = "C01",
    val locations: List<StorageLocation> = emptyList(),
    val recentManualSearches: List<String> = emptyList(),
    val manualSearchResults: List<ComponentDetail> = emptyList(),
    val isSearchingManual: Boolean = false,
    val manualSearchError: String? = null,
    val parsedPayload: InboundQrPayload? = null,
    val componentDetail: ComponentDetail? = null,
    val isLoadingComponent: Boolean = false,
    val componentLookupError: String? = null,
    val lastRawText: String? = null,
    val parseError: String? = null,
    val existingStockByPartNumber: Map<String, List<ExistingStockLocation>> = emptyMap()
)
