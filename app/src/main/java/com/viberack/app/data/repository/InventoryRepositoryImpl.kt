package com.viberack.app.data.repository

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.viberack.app.R
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.DashboardDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.dao.InventoryTransactionDao
import com.viberack.app.core.database.dao.StorageLocationDao
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.InventoryTransactionEntity
import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.InboundRecord
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StockOperation
import com.viberack.app.domain.model.StockOperationType
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.domain.model.StorageLocationSortMode
import com.viberack.app.domain.model.calculateDominantLocationCategoryProfile
import com.viberack.app.domain.repository.InventoryRepository
import com.viberack.app.domain.repository.StockPlacementRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InventoryRepositoryImpl(
    private val context: Context,
    private val database: RoomDatabase,
    private val componentDao: ComponentDao,
    private val dashboardDao: DashboardDao,
    private val storageLocationDao: StorageLocationDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val containerDao: ContainerDao,
    private val stockPlacementRepository: StockPlacementRepository,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val componentImageStore: ComponentImageStore,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) : InventoryRepository {
    private val legacyLocationContainerWriter = LegacyLocationContainerWriter(
        containerDao = containerDao,
        stockPlacementRepository = stockPlacementRepository
    )
    private val componentWriter = InventoryComponentWriter(
        componentDao = componentDao,
        inventoryItemDao = inventoryItemDao,
        protocolPartIdStrategy = protocolPartIdStrategy
    )
    private val inboundRecordPreparer = InboundRecordPreparer(componentImageStore)

    private companion object {
        private const val TAG = "InventoryRepository"
        private val LOCATION_CODE_REGEX = Regex("^[A-Z]\\d+$")
    }

    override fun observeDashboardSummary(): Flow<DashboardSummary> {
        return dashboardDao.observeSummary().map(InventoryReadModelMapper::toDashboardSummary)
    }

    override fun observeStorageLocations(): Flow<List<StorageLocation>> {
        return storageLocationDao.observeAll().map { locations ->
            locations.map(InventoryReadModelMapper::toStorageLocation)
        }
    }

    override fun observeStockLocationCells(): Flow<List<StockLocationCell>> {
        return storageLocationDao.observeLocationSummaries().map { locations ->
            locations.map(InventoryReadModelMapper::toStockLocationCell)
        }
    }

    override fun observeLocationInventory(locationId: Long): Flow<List<LocationInventoryItem>> {
        return inventoryItemDao.observeItemsByLocation(locationId).map { items ->
            items.map(InventoryReadModelMapper::toLocationInventoryItem)
        }
    }

    override fun observeLocationCategoryProfiles(): Flow<List<LocationCategoryProfile>> {
        return storageLocationDao.observeAll().map { locations ->
            locations.mapNotNull { location ->
                if (location.inboundProfileUpdatedAt <= 0L) {
                    null
                } else {
                    LocationCategoryProfile(
                        locationId = location.id,
                        category = location.inboundCategory,
                        packageName = location.inboundPackageName,
                        quantity = 1
                    )
                }
            }
        }
    }

    override fun observeSearchInventoryRecords(): Flow<List<SearchInventoryRecord>> {
        return inventoryItemDao.observeSearchInventoryRecords().map { items ->
            items.map(InventoryReadModelMapper::toSearchInventoryRecord)
        }
    }

    override suspend fun findExistingStockLocations(partNumber: String): List<ExistingStockLocation> {
        return inventoryItemDao.findExistingStockLocations(partNumber.trim().uppercase())
            .map(InventoryReadModelMapper::toExistingStockLocation)
    }

    override suspend fun refreshMissingLocationCategoryProfiles() {
        database.withTransaction {
            storageLocationDao.findLocationsMissingInboundProfile().forEach { location ->
                refreshLocationCategoryProfileInternal(location.id)
            }
        }
    }

    override suspend fun refreshAllLocationCategoryProfiles() {
        database.withTransaction {
            val profilesByLocation = inventoryItemDao.getAllLocationCategoryProfiles()
                .map(InventoryReadModelMapper::toLocationCategoryProfile)
                .groupBy(LocationCategoryProfile::locationId)
            storageLocationDao.getAll().forEach { location ->
                val profile = calculateDominantLocationCategoryProfile(profilesByLocation[location.id].orEmpty())
                storageLocationDao.updateInboundProfile(
                    locationId = location.id,
                    category = profile.category,
                    packageName = profile.packageName,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun refreshLocationCategoryProfile(locationId: Long) {
        database.withTransaction {
            refreshLocationCategoryProfileInternal(locationId)
        }
    }

    override suspend fun getNextManualInboundPartNumber(): String {
        val inStockC0PrefixedPartNumbers = inventoryItemDao.getInStockC0PrefixedPartNumbers()
        val parsedIndexes = inStockC0PrefixedPartNumbers
            .mapNotNull(ManualInboundPartNumberStrategy::parseIndex)
        val nextPartNumber = ManualInboundPartNumberStrategy.nextPartNumber(inStockC0PrefixedPartNumbers)
        Log.d(
            TAG,
            "getNextManualInboundPartNumber inStockC0PartNumbers=$inStockC0PrefixedPartNumbers, parsedIndexes=$parsedIndexes, nextPartNumber=$nextPartNumber"
        )
        return nextPartNumber
    }

    override suspend fun bootstrapDefaults() {
        val existingLocations = storageLocationDao.getAll()
        if (existingLocations.isNotEmpty()) {
            existingLocations.forEach { location ->
                legacyLocationContainerWriter.ensureContainer(location)
            }
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
        storageLocationDao.getAll().forEach { location ->
            legacyLocationContainerWriter.ensureContainer(location)
        }
    }

    override suspend fun addInbound(record: InboundRecord) {
        val preparedRecord = inboundRecordPreparer.prepare(record)
        val inboundAt = preparedRecord.inboundAt
        database.withTransaction {
            val location = findOrCreateLocation(preparedRecord.locationCode)
            val componentId = componentWriter.upsert(preparedRecord)
            val slot = legacyLocationContainerWriter.ensureContainer(location)
            val existingItem = inventoryItemDao.findByComponentAndLocation(componentId, location.id)

            val resolvedInventoryItem = if (existingItem == null) {
                val item = InventoryItemEntity(
                    componentId = componentId,
                    locationId = location.id,
                    quantity = preparedRecord.quantity,
                    lastInboundAt = inboundAt,
                    updatedAt = inboundAt
                )
                inventoryItemDao.insert(item)
                item
            } else {
                existingItem.copy(
                    quantity = existingItem.quantity + preparedRecord.quantity,
                    lastInboundAt = inboundAt,
                    updatedAt = inboundAt
                ).also { updated ->
                    inventoryItemDao.update(updated)
                }
            }
            legacyLocationContainerWriter.upsertStockItem(
                item = resolvedInventoryItem,
                slot = slot,
                updatedAt = inboundAt
            )
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.INBOUND,
                    containerId = location.id,
                    slotId = slot.id,
                    componentId = componentId,
                    quantityDelta = preparedRecord.quantity,
                    sourceType = preparedRecord.sourceType,
                    sourceRef = preparedRecord.component.partNumber,
                    rawPayload = preparedRecord.rawPayload,
                    createdAt = inboundAt
                )
            )

            inventoryTransactionDao.insert(
                InventoryTransactionEntity(
                    componentId = componentId,
                    locationId = location.id,
                    txnType = "INBOUND",
                    quantityDelta = preparedRecord.quantity,
                    sourceType = preparedRecord.sourceType,
                    sourceRef = preparedRecord.component.partNumber,
                    rawPayload = preparedRecord.rawPayload,
                    createdAt = inboundAt
                )
            )
            refreshLocationCategoryProfileInternal(location.id)
        }
        if (preparedRecord.sourceType == "MANUAL_INPUT") {
            Log.d(
                TAG,
                "addInbound skip enrichment for manual input partNumber=${preparedRecord.component.partNumber}"
            )
        } else {
            componentEnrichmentManager.schedule(preparedRecord.component.partNumber)
        }
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
        val duplicatedContainer = containerDao.findContainerByCode(normalizedCode)
        if (duplicatedContainer != null && duplicatedContainer.id != locationId) {
            return context.getString(R.string.inventory_error_location_code_exists)
        }
        val updatedLocation = location.copy(
            code = normalizedCode,
            displayName = displayName?.ifBlank { null },
            colorHex = colorHex?.ifBlank { null },
            sortMode = sortMode
        )
        database.withTransaction {
            storageLocationDao.update(updatedLocation)
            legacyLocationContainerWriter.ensureContainer(updatedLocation)
        }
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
        if (containerDao.findContainerByCode(normalizedCode) != null) {
            return false
        }
        return database.withTransaction {
            val location = StorageLocationEntity(
                code = normalizedCode,
                displayName = displayName?.ifBlank { normalizedCode } ?: normalizedCode,
                colorHex = colorHex?.ifBlank { null },
                sortMode = StorageLocationSortMode.NONE
            )
            val id = storageLocationDao.insert(location)
            if (id <= 0) {
                false
            } else {
                legacyLocationContainerWriter.ensureContainer(
                    location.copy(id = id)
                )
                true
            }
        }
    }

    override suspend fun deleteLocation(locationId: Long): String? {
        return database.withTransaction<String?> {
            val location = storageLocationDao.findById(locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            if (inventoryItemDao.countByLocation(locationId) > 0) {
                return@withTransaction context.getString(R.string.inventory_error_location_has_items)
            }
            inventoryTransactionDao.deleteByLocationId(location.id)
            containerDao.deleteContainerById(location.id)
            storageLocationDao.deleteById(location.id)
            null
        }
    }

    override suspend fun forceDeleteLocation(locationId: Long): String? {
        return database.withTransaction<String?> {
            val location = storageLocationDao.findById(locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            val slot = legacyLocationContainerWriter.ensureContainer(location)
            val items = inventoryItemDao.getAll().filter { it.locationId == location.id }
            val now = System.currentTimeMillis()
            items.forEach { item ->
                val component = componentDao.findById(item.componentId)
                stockPlacementRepository.recordOperation(
                    StockOperation(
                        type = StockOperationType.DELETE,
                        containerId = location.id,
                        slotId = slot.id,
                        componentId = item.componentId,
                        quantityDelta = -item.quantity,
                        sourceType = "MANUAL_DELETE_LOCATION",
                        sourceRef = component?.partNumber,
                        createdAt = now
                    )
                )
            }
            inventoryTransactionDao.deleteByLocationId(location.id)
            inventoryItemDao.deleteByLocationId(location.id)
            stockPlacementRepository.deleteContainerStock(location.id)
            refreshLocationCategoryProfileInternal(location.id)
            containerDao.deleteContainerById(location.id)
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
            val now = System.currentTimeMillis()
            val component = componentDao.findById(item.componentId)
            val updatedItem = item.copy(
                quantity = quantity,
                updatedAt = now
            )
            inventoryItemDao.update(updatedItem)
            val location = storageLocationDao.findById(item.locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            val slot = legacyLocationContainerWriter.ensureContainer(location)
            legacyLocationContainerWriter.upsertStockItem(
                item = updatedItem,
                slot = slot,
                updatedAt = now
            )
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.ADJUST,
                    containerId = item.locationId,
                    slotId = slot.id,
                    componentId = item.componentId,
                    quantityDelta = delta,
                    sourceType = "MANUAL_EDIT",
                    sourceRef = component?.partNumber,
                    createdAt = now
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
            refreshLocationCategoryProfileInternal(item.locationId)
            null
        }
    }

    override suspend fun updateInventoryItemSource(inventoryItemId: Long, sourceUrl: String?): String? {
        return database.withTransaction<String?> {
            val item = inventoryItemDao.findById(inventoryItemId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            val component = componentDao.findById(item.componentId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            val normalizedSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (component.sourceUrl == normalizedSourceUrl) {
                return@withTransaction null
            }
            componentDao.update(
                component.copy(
                    sourceUrl = normalizedSourceUrl,
                    updatedAt = System.currentTimeMillis()
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
            val sourceLocation = storageLocationDao.findById(item.locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            val sourceSlot = legacyLocationContainerWriter.ensureContainer(sourceLocation)
            val targetSlot = legacyLocationContainerWriter.ensureContainer(targetLocation)
            val targetItem = inventoryItemDao.findByComponentAndLocation(item.componentId, targetLocation.id)
            val now = System.currentTimeMillis()
            if (targetItem == null) {
                val movedItem = item.copy(
                    locationId = targetLocation.id,
                    updatedAt = now
                )
                inventoryItemDao.update(movedItem)
                stockPlacementRepository.deleteComponentFromSlot(item.componentId, sourceSlot.id)
                legacyLocationContainerWriter.upsertStockItem(
                    item = movedItem,
                    slot = targetSlot,
                    updatedAt = now
                )
            } else {
                val mergedItem = targetItem.copy(
                    quantity = targetItem.quantity + item.quantity,
                    updatedAt = now
                )
                inventoryItemDao.update(mergedItem)
                inventoryItemDao.deleteById(item.id)
                stockPlacementRepository.deleteComponentFromSlot(item.componentId, sourceSlot.id)
                legacyLocationContainerWriter.upsertStockItem(
                    item = mergedItem,
                    slot = targetSlot,
                    updatedAt = now
                )
            }
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.TRANSFER_OUT,
                    containerId = item.locationId,
                    slotId = sourceSlot.id,
                    componentId = item.componentId,
                    quantityDelta = -item.quantity,
                    sourceType = "MANUAL_TRANSFER",
                    sourceRef = component?.partNumber,
                    createdAt = now
                )
            )
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.TRANSFER_IN,
                    containerId = targetLocation.id,
                    slotId = targetSlot.id,
                    componentId = item.componentId,
                    quantityDelta = item.quantity,
                    sourceType = "MANUAL_TRANSFER",
                    sourceRef = component?.partNumber,
                    createdAt = now
                )
            )

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
            refreshLocationCategoryProfileInternal(item.locationId)
            refreshLocationCategoryProfileInternal(targetLocation.id)
            null
        }
    }

    override suspend fun deleteInventoryItem(inventoryItemId: Long): String? {
        return database.withTransaction<String?> {
            val item = inventoryItemDao.findById(inventoryItemId)
                ?: return@withTransaction context.getString(R.string.inventory_error_item_not_found)
            val component = componentDao.findById(item.componentId)
            val location = storageLocationDao.findById(item.locationId)
                ?: return@withTransaction context.getString(R.string.inventory_error_location_not_found)
            val slot = legacyLocationContainerWriter.ensureContainer(location)
            val now = System.currentTimeMillis()
            inventoryItemDao.deleteById(item.id)
            stockPlacementRepository.deleteComponentFromSlot(item.componentId, slot.id)
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.DELETE,
                    containerId = item.locationId,
                    slotId = slot.id,
                    componentId = item.componentId,
                    quantityDelta = -item.quantity,
                    sourceType = "MANUAL_DELETE",
                    sourceRef = component?.partNumber,
                    createdAt = now
                )
            )
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
            refreshLocationCategoryProfileInternal(item.locationId)
            null
        }
    }

    private suspend fun refreshLocationCategoryProfileInternal(locationId: Long) {
        val profile = calculateDominantLocationCategoryProfile(
            inventoryItemDao.getLocationCategoryProfiles(locationId)
                .map(InventoryReadModelMapper::toLocationCategoryProfile)
        )
        storageLocationDao.updateInboundProfile(
            locationId = locationId,
            category = profile.category,
            packageName = profile.packageName,
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun findOrCreateLocation(code: String): StorageLocationEntity {
        val normalizedCode = code.trim().uppercase()
        storageLocationDao.findByCode(normalizedCode)?.let { location ->
            legacyLocationContainerWriter.ensureContainer(location)
            return location
        }
        val duplicatedContainer = containerDao.findContainerByCode(normalizedCode)
        require(duplicatedContainer == null) {
            "Container code already exists without a legacy storage location: $normalizedCode"
        }

        val newId = storageLocationDao.insert(
            StorageLocationEntity(
                code = normalizedCode,
                displayName = normalizedCode,
                sortMode = StorageLocationSortMode.NONE
            )
        )

        val location = storageLocationDao.findByCode(normalizedCode)
            ?: StorageLocationEntity(
                id = newId,
                code = normalizedCode,
                displayName = normalizedCode,
                sortMode = StorageLocationSortMode.NONE
            )
        legacyLocationContainerWriter.ensureContainer(location)
        return location
    }



}
