package com.viberack.app.feature.inbound

import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.StorageLocation

data class InboundUiState(
    val defaultLocationCode: String? = null,
    val nextManualInboundPartNumber: String = "C01",
    val locations: List<StorageLocation> = emptyList(),
    val locationCategoryProfiles: List<LocationCategoryProfile> = emptyList(),
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
