package com.viberack.app.feature.home

import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.StorageLocation

data class HomeUiState(
    val summary: DashboardSummary = DashboardSummary(),
    val locations: List<StorageLocation> = emptyList(),
    val defaultLocationCode: String? = null
)
