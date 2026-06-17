package com.viberack.app.domain.repository

import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.SearchInventoryRecord
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeDashboardSummary(): Flow<DashboardSummary>
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>>
}
