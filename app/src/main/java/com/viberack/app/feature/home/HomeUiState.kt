package com.viberack.app.feature.home

import com.viberack.app.domain.model.DashboardSummary

data class HomeUiState(
    val summary: DashboardSummary = DashboardSummary()
)
