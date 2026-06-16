package com.example.lcsc_android_erp.data.repository

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.dao.DashboardDao
import com.example.lcsc_android_erp.core.database.dao.InventoryItemDao
import com.example.lcsc_android_erp.core.database.dao.InventoryTransactionDao
import com.example.lcsc_android_erp.core.database.dao.StorageLocationDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryTransactionEntity
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.DashboardSummary
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.InboundRecord
import com.example.lcsc_android_erp.domain.model.LocationCategoryProfile
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StockOperation
import com.example.lcsc_android_erp.domain.model.StockOperationType
import com.example.lcsc_android_erp.domain.model.StorageLocation
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import com.example.lcsc_android_erp.domain.model.calculateDominantLocationCategoryProfile
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementWrite
import java.io.File
import java.util.Locale
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
    private val containerDao: ContainerDao,
    private val stockPlacementRepository: StockPlacementRepository,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val componentImageStore: ComponentImageStore,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) : InventoryRepository {
    private companion object {
        private const val TAG = "InventoryRepository"
        private val LOCATION_CODE_REGEX = Regex("^[A-Z]\\d+$")
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
                    sourceUrl = item.sourceUrl,
                    specifications = parseSpecifications(item.specJson),
                    imageLocalPath = item.imageLocalPath,
                    imageUrl = null,
                    quantity = item.quantity,
                    lastInboundAt = item.lastInboundAt
                )
            }
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
            items.map { item ->
                SearchInventoryRecord(
                    inventoryItemId = item.inventoryItemId,
                    stockItemId = item.stockItemId,
                    isLegacyEditable = item.legacyInventoryItemId != null &&
                        item.containerType == ContainerType.LEGACY_LOCATION.name,
                    componentId = item.componentId,
                    partNumber = item.partNumber,
                    mpn = item.mpn,
                    name = item.name,
                    brand = item.brand,
                    packageName = item.packageName,
                    category = item.category,
                    description = item.description,
                    sourceUrl = item.sourceUrl,
                    specifications = parseSpecifications(item.specJson),
                    imageLocalPath = item.imageLocalPath,
                    quantity = item.quantity,
                    locationId = item.locationId,
                    locationCode = item.locationCode,
                    locationDisplayName = item.locationDisplayName,
                    locationColorHex = item.locationColorHex,
                    containerType = item.containerType.toContainerType(),
                    containerMacAddress = item.containerMacAddress,
                    slotId = item.slotId,
                    slotNumber = item.slotNumber,
                    slotCode = item.slotCode,
                    slotDisplayName = item.slotDisplayName
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
                .map(::toLocationCategoryProfile)
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
            .asSequence()
            .mapNotNull(::parseManualInboundIndex)
            .toList()
        val nextIndex = parsedIndexes.maxOrNull()?.plus(1) ?: 1
        val nextPartNumber = formatManualInboundPartNumber(nextIndex)
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
                ensureLegacyLocationContainer(location)
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
            ensureLegacyLocationContainer(location)
        }
    }

    override suspend fun addInbound(record: InboundRecord) {
        val preparedRecord = prepareInboundRecord(record)
        val inboundAt = preparedRecord.inboundAt
        database.withTransaction {
            val location = findOrCreateLocation(preparedRecord.locationCode)
            val componentId = upsertComponent(preparedRecord)
            val slot = ensureLegacyLocationContainer(location)
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
            upsertStockItemForLegacyInventoryItem(
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
            ensureLegacyLocationContainer(updatedLocation)
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
                ensureLegacyLocationContainer(
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
            val slot = ensureLegacyLocationContainer(location)
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
            val slot = ensureLegacyLocationContainer(location)
            upsertStockItemForLegacyInventoryItem(
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
            val sourceSlot = ensureLegacyLocationContainer(sourceLocation)
            val targetSlot = ensureLegacyLocationContainer(targetLocation)
            val targetItem = inventoryItemDao.findByComponentAndLocation(item.componentId, targetLocation.id)
            val now = System.currentTimeMillis()
            if (targetItem == null) {
                val movedItem = item.copy(
                    locationId = targetLocation.id,
                    updatedAt = now
                )
                inventoryItemDao.update(movedItem)
                stockPlacementRepository.deleteComponentFromSlot(item.componentId, sourceSlot.id)
                upsertStockItemForLegacyInventoryItem(
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
                upsertStockItemForLegacyInventoryItem(
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
            val slot = ensureLegacyLocationContainer(location)
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
            inventoryItemDao.getLocationCategoryProfiles(locationId).map(::toLocationCategoryProfile)
        )
        storageLocationDao.updateInboundProfile(
            locationId = locationId,
            category = profile.category,
            packageName = profile.packageName,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun toLocationCategoryProfile(
        projection: com.example.lcsc_android_erp.core.database.model.LocationCategoryProfileProjection
    ): LocationCategoryProfile {
        return LocationCategoryProfile(
            locationId = projection.locationId,
            category = projection.category,
            packageName = projection.packageName,
            quantity = projection.quantity
        )
    }

    private suspend fun findOrCreateLocation(code: String): StorageLocationEntity {
        val normalizedCode = code.trim().uppercase()
        storageLocationDao.findByCode(normalizedCode)?.let { location ->
            ensureLegacyLocationContainer(location)
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
        ensureLegacyLocationContainer(location)
        return location
    }

    private suspend fun ensureLegacyLocationContainer(location: StorageLocationEntity): ContainerSlotEntity {
        val now = System.currentTimeMillis()
        val container = containerDao.findContainerById(location.id)
        if (container == null) {
            val insertId = containerDao.insertContainer(
                ContainerEntity(
                    id = location.id,
                    code = location.code,
                    displayName = location.displayName,
                    type = ContainerType.LEGACY_LOCATION.name,
                    slotCount = 1,
                    colorHex = location.colorHex,
                    sortMode = location.sortMode,
                    remark = location.remark,
                    createdAt = location.createdAt,
                    updatedAt = now
                )
            )
            if (insertId <= 0) {
                containerDao.findContainerById(location.id)?.let { existing ->
                    containerDao.updateContainer(existing.toLegacyContainer(location, now))
                }
            }
        } else {
            containerDao.updateContainer(container.toLegacyContainer(location, now))
        }

        val slot = containerDao.findSlotByContainerAndNumber(location.id, 1)
        if (slot != null) {
            val updatedSlot = slot.copy(
                slotCode = location.code,
                displayName = location.displayName ?: location.code,
                sortOrder = 1,
                updatedAt = now
            )
            if (updatedSlot != slot) {
                containerDao.updateSlot(updatedSlot)
            }
            return updatedSlot
        }

        val newSlot = ContainerSlotEntity(
            containerId = location.id,
            slotNumber = 1,
            slotCode = location.code,
            displayName = location.displayName ?: location.code,
            sortOrder = 1,
            createdAt = location.createdAt,
            updatedAt = now
        )
        val slotId = containerDao.insertSlot(newSlot)
        return containerDao.findSlotByContainerAndNumber(location.id, 1)
            ?: newSlot.copy(id = slotId)
    }

    private fun ContainerEntity.toLegacyContainer(
        location: StorageLocationEntity,
        updatedAt: Long
    ): ContainerEntity {
        return copy(
            code = location.code,
            displayName = location.displayName,
            type = ContainerType.LEGACY_LOCATION.name,
            slotCount = 1,
            colorHex = location.colorHex,
            sortMode = location.sortMode,
            remark = location.remark,
            updatedAt = updatedAt
        )
    }

    private suspend fun upsertStockItemForLegacyInventoryItem(
        item: InventoryItemEntity,
        slot: ContainerSlotEntity,
        updatedAt: Long
    ) {
        stockPlacementRepository.upsertStock(
            StockPlacementWrite(
                componentId = item.componentId,
                containerId = item.locationId,
                slotId = slot.id,
                quantity = item.quantity,
                lastInboundAt = item.lastInboundAt,
                updatedAt = updatedAt
            )
        )
    }

    private suspend fun prepareInboundRecord(record: InboundRecord): InboundRecord {
        val existingLocalPath = record.component.imageLocalPath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { path ->
                runCatching { File(path).exists() && File(path).length() > 0L }.getOrDefault(false)
            }
        if (existingLocalPath != null) {
            return record
        }
        val persistedLocalPath = componentImageStore.persistImage(
            partNumber = record.component.partNumber,
            imageUrl = record.component.imageUrl
        ) ?: return record
        return record.copy(
            component = record.component.copy(
                imageLocalPath = persistedLocalPath
            )
        )
    }

    private suspend fun upsertComponent(record: InboundRecord): Long {
        val normalizedPartNumber = record.component.partNumber.trim().uppercase()
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        val specJson = record.component.specifications
            .takeIf { it.isNotEmpty() }
            ?.let { JSONObject(it).toString() }
        if (existing != null) {
            val shouldResetStaleManualComponent = record.sourceType == "MANUAL_INPUT" &&
                inventoryItemDao.countByComponent(existing.id) == 0
            val protocolPartId = existing.protocolPartId
                ?: protocolPartIdStrategy.forComponent(
                    componentId = existing.id,
                    partNumber = normalizedPartNumber,
                    sourceType = record.sourceType
                )
            val updated = if (shouldResetStaleManualComponent) {
                existing.copy(
                    partNumber = normalizedPartNumber,
                    protocolPartId = protocolPartIdStrategy.forComponent(
                        componentId = existing.id,
                        partNumber = normalizedPartNumber,
                        sourceType = record.sourceType
                    ),
                    mpn = record.component.mpn,
                    name = record.component.name,
                    brand = record.component.brand,
                    packageName = record.component.packageName,
                    category = record.component.category,
                    specJson = specJson,
                    description = record.component.description,
                    sourceUrl = record.component.productUrl,
                    imageLocalPath = record.component.imageLocalPath,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                existing.copy(
                    partNumber = normalizedPartNumber,
                    protocolPartId = protocolPartId,
                    mpn = existing.mpn ?: record.component.mpn,
                    name = existing.name ?: record.component.name,
                    brand = existing.brand ?: record.component.brand,
                    packageName = existing.packageName ?: record.component.packageName,
                    category = existing.category ?: record.component.category,
                    specJson = existing.specJson ?: specJson,
                    description = existing.description ?: record.component.description,
                    sourceUrl = existing.sourceUrl ?: record.component.productUrl,
                    imageLocalPath = existing.imageLocalPath ?: record.component.imageLocalPath,
                    updatedAt = System.currentTimeMillis()
                )
            }
            if (shouldResetStaleManualComponent) {
                Log.d(
                    TAG,
                    "upsertComponent reset stale manual component partNumber=$normalizedPartNumber, existingId=${existing.id}, previousImage=${existing.imageLocalPath}, newImage=${record.component.imageLocalPath}"
                )
            }
            if (updated != existing) {
                componentDao.update(updated)
            }
            return existing.id
        }

        val componentEntity = ComponentEntity(
            partNumber = normalizedPartNumber,
            protocolPartId = protocolPartIdStrategy.forComponent(
                componentId = null,
                partNumber = normalizedPartNumber,
                sourceType = record.sourceType
            ),
            mpn = record.component.mpn,
            name = record.component.name,
            brand = record.component.brand,
            packageName = record.component.packageName,
            category = record.component.category,
            specJson = specJson,
            description = record.component.description,
            sourceUrl = record.component.productUrl,
            imageLocalPath = record.component.imageLocalPath
        )

        val insertId = componentDao.insert(componentEntity)
        if (insertId > 0) {
            val resolvedProtocolPartId = protocolPartIdStrategy.forComponent(
                componentId = insertId,
                partNumber = normalizedPartNumber,
                sourceType = record.sourceType
            )
            if (resolvedProtocolPartId != componentEntity.protocolPartId) {
                componentDao.update(
                    componentEntity.copy(
                        id = insertId,
                        protocolPartId = resolvedProtocolPartId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
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

    private fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }
}

private fun parseManualInboundIndex(partNumber: String?): Int? {
    return partNumber
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("^C0\\d+$")) }
        ?.removePrefix("C0")
        ?.toIntOrNull()
}

private fun formatManualInboundPartNumber(index: Int): String {
    return "C0$index"
}
