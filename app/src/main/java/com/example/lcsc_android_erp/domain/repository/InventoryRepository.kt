package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.DashboardSummary
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.InboundRecord
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocation
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeDashboardSummary(): Flow<DashboardSummary>
    fun observeStorageLocations(): Flow<List<StorageLocation>>
    fun observeStockLocationCells(): Flow<List<StockLocationCell>>
    fun observeLocationInventory(locationId: Long): Flow<List<LocationInventoryItem>>
    fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>>
    suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocation>
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
