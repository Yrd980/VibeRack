package com.viberack.app.data.repository

import android.content.Context
import androidx.room.RoomDatabase
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.DashboardDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.repository.InventoryRepository
import com.viberack.app.domain.repository.StockPlacementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InventoryRepositoryImpl(
    @Suppress("unused") private val context: Context,
    @Suppress("unused") private val database: RoomDatabase,
    @Suppress("unused") private val componentDao: ComponentDao,
    private val dashboardDao: DashboardDao,
    private val stockItemDao: StockItemDao,
    @Suppress("unused") private val containerDao: ContainerDao,
    @Suppress("unused") private val stockPlacementRepository: StockPlacementRepository,
    @Suppress("unused") private val componentEnrichmentManager: ComponentEnrichmentManager,
    @Suppress("unused") private val componentImageStore: ComponentImageStore,
    @Suppress("unused") private val protocolPartIdStrategy: ProtocolPartIdStrategy
) : InventoryRepository {
    override fun observeDashboardSummary(): Flow<DashboardSummary> {
        return dashboardDao.observeSummary().map(InventoryReadModelMapper::toDashboardSummary)
    }

    override fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>> {
        return stockItemDao.observeSearchInventoryRecords().map { items ->
            items.map(InventoryReadModelMapper::toSearchInventoryRecord)
        }
    }
}
