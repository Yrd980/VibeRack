package com.viberack.app.domain.repository

import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.InboundRecord
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeDashboardSummary(): Flow<DashboardSummary>
    fun observeStorageLocations(): Flow<List<StorageLocation>>
    fun observeStockLocationCells(): Flow<List<StockLocationCell>>
    fun observeLocationInventory(locationId: Long): Flow<List<LocationInventoryItem>>
    fun observeLocationCategoryProfiles(): Flow<List<LocationCategoryProfile>>
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>>
    suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocation>
    suspend fun refreshMissingLocationCategoryProfiles()
    suspend fun refreshAllLocationCategoryProfiles()
    suspend fun refreshLocationCategoryProfile(locationId: Long)
    suspend fun getNextManualInboundPartNumber(): String
    suspend fun bootstrapDefaults()
    suspend fun addInbound(record: InboundRecord)
    suspend fun addStorageLocation(code: String, displayName: String?, colorHex: String?): Boolean
    suspend fun updateLocation(locationId: Long, code: String, displayName: String?, colorHex: String?, sortMode: String): String?
    suspend fun deleteLocation(locationId: Long): String?
    suspend fun forceDeleteLocation(locationId: Long): String?
    suspend fun updateInventoryItemQuantity(inventoryItemId: Long, quantity: Int): String?
    suspend fun updateInventoryItemSource(inventoryItemId: Long, sourceUrl: String?): String?
    suspend fun transferInventoryItem(inventoryItemId: Long, targetLocationCode: String): String?
    suspend fun deleteInventoryItem(inventoryItemId: Long): String?
}
