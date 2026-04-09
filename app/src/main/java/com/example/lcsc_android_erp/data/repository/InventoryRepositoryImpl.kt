package com.example.lcsc_android_erp.data.repository

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.DashboardDao
import com.example.lcsc_android_erp.core.database.dao.InventoryItemDao
import com.example.lcsc_android_erp.core.database.dao.InventoryTransactionDao
import com.example.lcsc_android_erp.core.database.dao.StorageLocationDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryTransactionEntity
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity
import com.example.lcsc_android_erp.domain.model.DashboardSummary
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.InboundRecord
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocation
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class InventoryRepositoryImpl(
    private val context: Context,
    private val database: RoomDatabase,
    private val componentDao: ComponentDao,
    private val dashboardDao: DashboardDao,
    private val storageLocationDao: StorageLocationDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val componentEnrichmentManager: ComponentEnrichmentManager
) : InventoryRepository {
    private companion object {
        private val LOCATION_CODE_REGEX = Regex("^[A-Z][1-9]$")
    }

    override fun observeDashboardSummary(): Flow<DashboardSummary> {
        return dashboardDao.observeSummary().map { summary ->
            DashboardSummary(
                componentCount = summary.componentCount,
                locationCount = summary.locationCount,
                inventoryCount = summary.inventoryCount,
                totalQuantity = summary.totalQuantity,
                transactionCount = summary.transactionCount
            )
        }
    }

    override fun observeStorageLocations(): Flow<List<StorageLocation>> {
        return storageLocationDao.observeAll().map { locations ->
            locations.map { location ->
                StorageLocation(
                    id = location.id,
                    code = location.code,
                    displayName = location.displayName,
                    colorHex = location.colorHex,
                    sortMode = location.sortMode,
                    remark = location.remark
                )
            }
        }
    }

    override fun observeStockLocationCells(): Flow<List<StockLocationCell>> {
        return storageLocationDao.observeLocationSummaries().map { locations ->
            locations.map { location ->
                StockLocationCell(
                    id = location.id,
                    code = location.code,
                    displayName = location.displayName,
                    colorHex = location.colorHex,
                    sortMode = location.sortMode,
                    remark = location.remark,
                    inventoryItemCount = location.inventoryItemCount,
                    totalQuantity = location.totalQuantity
                )
            }
        }
    }

    override fun observeLocationInventory(locationId: Long): Flow<List<LocationInventoryItem>> {
        return inventoryItemDao.observeItemsByLocation(locationId).map { items ->
            items.map { item ->
                LocationInventoryItem(
                    inventoryItemId = item.inventoryItemId,
                    componentId = item.componentId,
                    partNumber = item.partNumber,
                    mpn = item.mpn,
                    name = item.name,
                    brand = item.brand,
                    packageName = item.packageName,
                    category = item.category,
                    description = item.description,
                    specifications = parseSpecifications(item.specJson),
                    imageLocalPath = item.imageLocalPath,
                    imageUrl = null,
                    quantity = item.quantity,
                    lastInboundAt = item.lastInboundAt
                )
            }
        }
    }

    override fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>> {
        return inventoryItemDao.observeSearchInventoryRecords().map { items ->
            items.map { item ->
                SearchInventoryRecord(
                    inventoryItemId = item.inventoryItemId,
                    componentId = item.componentId,
                    partNumber = item.partNumber,
                    mpn = item.mpn,
                    name = item.name,
                    brand = item.brand,
                    packageName = item.packageName,
                    category = item.category,
                    description = item.description,
                    specifications = parseSpecifications(item.specJson),
                    imageLocalPath = item.imageLocalPath,
                    quantity = item.quantity,
                    locationId = item.locationId,
                    locationCode = item.locationCode,
                    locationDisplayName = item.locationDisplayName,
                    locationColorHex = item.locationColorHex
                )
            }
        }
    }

    override suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocation> {
        return inventoryItemDao.findExistingStockLocations(partNumber.trim().uppercase()).map { item ->
            ExistingStockLocation(
                locationCode = item.locationCode,
                locationDisplayName = item.locationDisplayName,
                quantity = item.quantity
            )
        }
    }

    override suspend fun bootstrapDefaults() {
        if (storageLocationDao.count() > 0) {
            return
        }

        storageLocationDao.insertAll(
            listOf(
                StorageLocationEntity(
                    code = "A1",
                    displayName = "A1",
                    sortMode = StorageLocationSortMode.NONE,
                    remark = "默认库位"
                ),
                StorageLocationEntity(
                    code = "C3",
                    displayName = "C3",
                    sortMode = StorageLocationSortMode.NONE,
                    remark = "默认库位"
                )
            )
        )
    }

    override suspend fun addInbound(record: InboundRecord) {
        val inboundAt = record.inboundAt
        database.withTransaction {
            val location = findOrCreateLocation(record.locationCode)
            val componentId = upsertComponent(record)
            val existingItem = inventoryItemDao.findByComponentAndLocation(componentId, location.id)

            if (existingItem == null) {
                inventoryItemDao.insert(
                    InventoryItemEntity(
                        componentId = componentId,
                        locationId = location.id,
                        quantity = record.quantity,
                        lastInboundAt = inboundAt,
                        updatedAt = inboundAt
                    )
                )
            } else {
                inventoryItemDao.update(
                    existingItem.copy(
                        quantity = existingItem.quantity + record.quantity,
                        lastInboundAt = inboundAt,
                        updatedAt = inboundAt
                    )
                )
            }

            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = componentId,
                    locationId = location.id,
                    txnType = "INBOUND",
                    quantityDelta = record.quantity,
                    sourceType = record.sourceType,
                    sourceRef = record.component.partNumber,
                    rawPayload = record.rawPayload,
                    createdAt = inboundAt
                )
            )
        }
        componentEnrichmentManager.schedule(record.component.partNumber)
    }

    override suspend fun updateLocation(
        locationId: Long,
        code: String,
        displayName: String?,
        colorHex: String?,
        sortMode: String
    ): String? {
        val location = storageLocationDao.findById(locationId)
            ?: return context.getString(R.string.inventory_error_location_not_found)
        val normalizedCode = code.trim().uppercase()
        if (!LOCATION_CODE_REGEX.matches(normalizedCode)) {
            return context.getString(R.string.inventory_error_location_code_format)
        }
        val duplicated = storageLocationDao.findByCode(normalizedCode)
        if (duplicated != null && duplicated.id != locationId) {
            return context.getString(R.string.inventory_error_location_code_exists)
        }
        storageLocationDao.update(
            location.copy(
                code = normalizedCode,
                displayName = displayName?.ifBlank { null },
                colorHex = colorHex?.ifBlank { null },
                sortMode = sortMode
            )
        )
        return null
    }

    override suspend fun addStorageLocation(code: String, displayName: String?, colorHex: String?): Boolean {
        val normalizedCode = code.trim().uppercase()
        if (!LOCATION_CODE_REGEX.matches(normalizedCode)) {
            return false
        }
        if (storageLocationDao.findByCode(normalizedCode) != null) {
            return false
        }
        return storageLocationDao.insert(
            StorageLocationEntity(
                code = normalizedCode,
                displayName = displayName?.ifBlank { normalizedCode } ?: normalizedCode,
                colorHex = colorHex?.ifBlank { null },
                sortMode = StorageLocationSortMode.NONE
            )
        ) > 0
    }

    override suspend fun deleteLocation(locationId: Long): String? {
        return database.withTransaction<String?> {
            val location = storageLocationDao.findById(locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            if (inventoryItemDao.countByLocation(locationId) > 0) {
                return@withTransaction context.getString(R.string.inventory_error_location_has_items)
            }
            inventoryTransactionDao.deleteByLocationId(location.id)
            storageLocationDao.deleteById(location.id)
            null
        }
    }

    override suspend fun forceDeleteLocation(locationId: Long): String? {
        return database.withTransaction<String?> {
            val location = storageLocationDao.findById(locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            inventoryTransactionDao.deleteByLocationId(location.id)
            inventoryItemDao.deleteByLocationId(location.id)
            storageLocationDao.deleteById(location.id)
            null
        }
    }

    override suspend fun updateInventoryItemQuantity(inventoryItemId: Long, quantity: Int): String? {
        if (quantity < 0) {
            return context.getString(R.string.inventory_error_quantity_negative)
        }

        return database.withTransaction<String?> {
            val item = inventoryItemDao.findById(inventoryItemId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            if (item.quantity == quantity) {
                return@withTransaction null
            }

            val delta = quantity - item.quantity
            val component = componentDao.findById(item.componentId)
            inventoryItemDao.update(
                item.copy(
                    quantity = quantity,
                    updatedAt = System.currentTimeMillis()
                )
            )
            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = item.componentId,
                    locationId = item.locationId,
                    txnType = "ADJUST",
                    quantityDelta = delta,
                    sourceType = "MANUAL_EDIT",
                    sourceRef = component?.partNumber
                )
            )
            null
        }
    }

    override suspend fun transferInventoryItem(inventoryItemId: Long, targetLocationCode: String): String? {
        val normalizedCode = targetLocationCode.trim().uppercase()
        if (normalizedCode.isBlank()) {
            return context.getString(R.string.inventory_error_target_location_required)
        }

        return database.withTransaction<String?> {
            val item = inventoryItemDao.findById(inventoryItemId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            val targetLocation = storageLocationDao.findByCode(normalizedCode)
                ?: return@withTransaction context.getString(R.string.inventory_error_target_location_not_found)
            if (targetLocation.id == item.locationId) {
                return@withTransaction context.getString(R.string.inventory_error_target_location_same)
            }

            val component = componentDao.findById(item.componentId)
            val targetItem = inventoryItemDao.findByComponentAndLocation(item.componentId, targetLocation.id)
            if (targetItem == null) {
                inventoryItemDao.update(
                    item.copy(
                        locationId = targetLocation.id,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                inventoryItemDao.update(
                    targetItem.copy(
                        quantity = targetItem.quantity + item.quantity,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                inventoryItemDao.deleteById(item.id)
            }

            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = item.componentId,
                    locationId = item.locationId,
                    txnType = "TRANSFER_OUT",
                    quantityDelta = -item.quantity,
                    sourceType = "MANUAL_TRANSFER",
                    sourceRef = component?.partNumber
                )
            )
            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = item.componentId,
                    locationId = targetLocation.id,
                    txnType = "TRANSFER_IN",
                    quantityDelta = item.quantity,
                    sourceType = "MANUAL_TRANSFER",
                    sourceRef = component?.partNumber
                )
            )
            null
        }
    }

    override suspend fun deleteInventoryItem(inventoryItemId: Long): String? {
        return database.withTransaction<String?> {
            val item = inventoryItemDao.findById(inventoryItemId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            val component = componentDao.findById(item.componentId)
            inventoryItemDao.deleteById(item.id)
            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = item.componentId,
                    locationId = item.locationId,
                    txnType = "DELETE",
                    quantityDelta = -item.quantity,
                    sourceType = "MANUAL_DELETE",
                    sourceRef = component?.partNumber
                )
            )
            null
        }
    }

    private suspend fun findOrCreateLocation(code: String): StorageLocationEntity {
        val normalizedCode = code.trim().uppercase()
        storageLocationDao.findByCode(normalizedCode)?.let { return it }

        val newId = storageLocationDao.insert(
            StorageLocationEntity(
                code = normalizedCode,
                displayName = normalizedCode,
                sortMode = StorageLocationSortMode.NONE
            )
        )

        return storageLocationDao.findByCode(normalizedCode)
            ?: StorageLocationEntity(
                id = newId,
                code = normalizedCode,
                displayName = normalizedCode,
                sortMode = StorageLocationSortMode.NONE
            )
    }

    private suspend fun upsertComponent(record: InboundRecord): Long {
        val normalizedPartNumber = record.component.partNumber.trim().uppercase()
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        if (existing != null) {
            val updated = existing.copy(
                partNumber = normalizedPartNumber,
                brand = existing.brand ?: record.component.brand,
                packageName = existing.packageName ?: record.component.packageName,
                category = existing.category ?: record.component.category,
                updatedAt = System.currentTimeMillis()
            )
            if (updated != existing) {
                componentDao.update(updated)
            }
            return existing.id
        }

        val componentEntity = ComponentEntity(
            partNumber = normalizedPartNumber,
            brand = record.component.brand,
            packageName = record.component.packageName,
            category = record.component.category
        )

        val insertId = componentDao.insert(componentEntity)
        if (insertId > 0) {
            return insertId
        }

        return componentDao.findByPartNumber(normalizedPartNumber)?.id
            ?: error("Failed to resolve component id for $normalizedPartNumber")
    }

    private fun parseSpecifications(specJson: String?): Map<String, String> {
        if (specJson.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val json = JSONObject(specJson)
            json.keys().asSequence().associateWith { key ->
                json.optString(key)
            }.filterValues { value ->
                value.isNotBlank() && value != "null"
            }
        }.getOrDefault(emptyMap())
    }
}
