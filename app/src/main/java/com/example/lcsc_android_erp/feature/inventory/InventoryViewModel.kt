package com.example.lcsc_android_erp.feature.inventory

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lcsc_android_erp.core.AppContainer
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.network.isNetworkAvailable
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.domain.model.LocationInventoryItem
import com.example.lcsc_android_erp.domain.model.StockLocationCell
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import com.example.lcsc_android_erp.domain.repository.LcscCatalogRepository
import com.example.lcsc_android_erp.feature.inbound.LcscQrParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Comparator
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val lcscCatalogRepository: LcscCatalogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appContext: Context
) : ViewModel() {
    private val selectedLocation = MutableStateFlow<StockLocationCell?>(null)
    private val pendingOpenRequest = MutableStateFlow<InventoryOpenRequest?>(null)
    private val settingsLocationId = MutableStateFlow<Long?>(null)
    private val addLocationError = MutableStateFlow<String?>(null)
    private val updateLocationError = MutableStateFlow<String?>(null)
    private val selectedLocationItemsFlow = selectedLocation.flatMapLatest { location ->
        if (location == null) {
            flowOf(emptyList<LocationInventoryItem>())
        } else {
            inventoryRepository.observeLocationInventory(location.id)
        }
    }
    private val locationDataFlow = combine(
        inventoryRepository.observeStockLocationCells(),
        inventoryRepository.observeStorageLocations()
    ) { cells, locations ->
        cells to locations
    }
    private val settingsLocationItemsFlow = settingsLocationId.flatMapLatest { locationId ->
        if (locationId == null) {
            flowOf(emptyList<LocationInventoryItem>())
        } else {
            inventoryRepository.observeLocationInventory(locationId)
        }
    }
    private val locationUiStateFlow = combine(
        locationDataFlow,
        selectedLocation,
        pendingOpenRequest,
        addLocationError,
        updateLocationError
    ) { locationData, selected, openRequest, addError, updateError ->
        val (cells, locations) = locationData
        InventoryUiState(
            cells = cells,
            locations = locations,
            selectedLocation = selected,
            pendingOpenRequest = openRequest,
            addLocationError = addError,
            updateLocationError = updateError
        )
    }
    private val baseUiStateFlow = combine(
        locationUiStateFlow,
        selectedLocationItemsFlow
    ) { locationState, items ->
        InventoryUiState(
            cells = locationState.cells,
            locations = locationState.locations,
            selectedLocation = locationState.selectedLocation,
            selectedLocationItems = sortLocationItems(
                items = items,
                sortMode = locationState.selectedLocation?.sortMode ?: StorageLocationSortMode.NONE
            ),
            pendingOpenRequest = locationState.pendingOpenRequest,
            addLocationError = locationState.addLocationError,
            updateLocationError = locationState.updateLocationError
        )
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        baseUiStateFlow,
        settingsLocationItemsFlow,
        userPreferencesRepository.preferences
    ) { baseState, settingsItems, preferences ->
        baseState.copy(
            selectedLocationItems = baseState.selectedLocationItems,
            settingsLocationSortAttributes = supportedSpecificationKeys(settingsItems),
            recentLocationColors = preferences.recentLocationColors
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState()
    )

    init {
        viewModelScope.launch {
            inventoryRepository.bootstrapDefaults()
        }
    }

    fun onLocationSelected(location: StockLocationCell) {
        selectedLocation.value = location
    }

    fun dismissLocationDetail() {
        selectedLocation.value = null
    }

    fun openInventoryLocation(locationCode: String) {
        val normalizedLocationCode = locationCode.trim().uppercase(Locale.ROOT)
        if (normalizedLocationCode.isBlank()) {
            return
        }

        viewModelScope.launch {
            val targetLocation = inventoryRepository.observeStockLocationCells()
                .first()
                .firstOrNull { it.code.equals(normalizedLocationCode, ignoreCase = true) }
                ?: return@launch

            pendingOpenRequest.value = InventoryOpenRequest(
                locationCode = targetLocation.code,
            )
            selectedLocation.value = targetLocation
        }
    }

    fun openInventoryItem(locationCode: String, partNumber: String) {
        val normalizedLocationCode = locationCode.trim().uppercase(Locale.ROOT)
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        if (normalizedLocationCode.isBlank() || normalizedPartNumber.isBlank()) {
            return
        }

        viewModelScope.launch {
            val targetLocation = inventoryRepository.observeStockLocationCells()
                .first()
                .firstOrNull { it.code.equals(normalizedLocationCode, ignoreCase = true) }
                ?: return@launch

            pendingOpenRequest.value = InventoryOpenRequest(
                locationCode = targetLocation.code,
                partNumber = normalizedPartNumber
            )
            selectedLocation.value = targetLocation
        }
    }

    fun openFirstInventoryItem(partNumber: String) {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        if (normalizedPartNumber.isBlank()) {
            return
        }

        viewModelScope.launch {
            val targetLocation = inventoryRepository.findExistingStockLocations(normalizedPartNumber)
                .firstOrNull()
                ?: return@launch
            openInventoryItem(targetLocation.locationCode, normalizedPartNumber)
        }
    }

    fun clearPendingOpenRequest() {
        pendingOpenRequest.value = null
    }

    fun openLocationSettings(locationId: Long) {
        settingsLocationId.value = locationId
    }

    fun closeLocationSettings() {
        settingsLocationId.value = null
    }

    fun clearAddLocationError() {
        addLocationError.value = null
    }

    fun clearUpdateLocationError() {
        updateLocationError.value = null
    }

    fun addStorageLocation(code: String, displayName: String?, colorHex: String?) {
        viewModelScope.launch {
            addLocationError.value = null
            val created = inventoryRepository.addStorageLocation(code, displayName, colorHex)
            addLocationError.value = if (created) {
                null
            } else {
                appContext.getString(R.string.inventory_add_location_invalid)
            }
        }
    }

    fun updateLocation(
        locationId: Long,
        code: String,
        displayName: String?,
        colorHex: String?,
        sortMode: String,
        onCompleted: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            updateLocationError.value = inventoryRepository.updateLocation(
                locationId = locationId,
                code = code,
                displayName = displayName,
                colorHex = colorHex,
                sortMode = sortMode
            )
            if (updateLocationError.value == null) {
                selectedLocation.update { current ->
                    current?.takeIf { it.id == locationId }?.copy(
                        code = code.trim().uppercase(),
                        displayName = displayName?.ifBlank { null },
                        colorHex = colorHex?.ifBlank { null },
                        sortMode = sortMode
                    ) ?: current
                }
            }
            onCompleted(updateLocationError.value)
        }
    }

    fun deleteLocation(
        locationId: Long,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            updateLocationError.value = null
            val error = inventoryRepository.deleteLocation(locationId)
            updateLocationError.value = error
            if (error == null) {
                selectedLocation.update { current ->
                    current?.takeIf { it.id == locationId }?.let { null } ?: current
                }
            }
            onCompleted(error)
        }
    }

    fun forceDeleteLocation(
        locationId: Long,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            updateLocationError.value = null
            val error = inventoryRepository.forceDeleteLocation(locationId)
            updateLocationError.value = error
            if (error == null) {
                selectedLocation.update { current ->
                    current?.takeIf { it.id == locationId }?.let { null } ?: current
                }
            }
            onCompleted(error)
        }
    }

    fun updateInventoryItemQuantity(
        inventoryItemId: Long,
        quantity: Int,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onCompleted(inventoryRepository.updateInventoryItemQuantity(inventoryItemId, quantity))
        }
    }

    fun updateInventoryItemSource(
        inventoryItemId: Long,
        sourceUrl: String?,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onCompleted(inventoryRepository.updateInventoryItemSource(inventoryItemId, sourceUrl))
        }
    }

    fun transferInventoryItem(
        inventoryItemId: Long,
        targetLocationCode: String,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onCompleted(inventoryRepository.transferInventoryItem(inventoryItemId, targetLocationCode))
        }
    }

    fun transferInventoryItems(
        inventoryItemIds: List<Long>,
        targetLocationCode: String,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedTarget = targetLocationCode.trim().uppercase(Locale.ROOT)
            if (inventoryItemIds.isEmpty() || normalizedTarget.isBlank()) {
                onCompleted(appContext.getString(R.string.inventory_transfer_items_empty))
                return@launch
            }
            inventoryItemIds.distinct().forEach { inventoryItemId ->
                val error = inventoryRepository.transferInventoryItem(inventoryItemId, normalizedTarget)
                if (error != null) {
                    onCompleted(error)
                    return@launch
                }
            }
            onCompleted(null)
        }
    }

    fun deleteInventoryItem(
        inventoryItemId: Long,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onCompleted(inventoryRepository.deleteInventoryItem(inventoryItemId))
        }
    }

    fun deleteInventoryItems(
        inventoryItemIds: List<Long>,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            if (inventoryItemIds.isEmpty()) {
                onCompleted(appContext.getString(R.string.inventory_delete_items_empty))
                return@launch
            }
            inventoryItemIds.distinct().forEach { inventoryItemId ->
                val error = inventoryRepository.deleteInventoryItem(inventoryItemId)
                if (error != null) {
                    onCompleted(error)
                    return@launch
                }
            }
            onCompleted(null)
        }
    }

    fun lookupScannedComponent(
        rawText: String,
        onCompleted: (InventoryScanLookupResult) -> Unit
    ) {
        val payload = LcscQrParser.parse(rawText)
        if (payload == null) {
            onCompleted(
                InventoryScanLookupResult(
                    errorMessage = appContext.getString(R.string.inventory_scan_invalid_qr)
                )
            )
            return
        }

        if (!appContext.isNetworkAvailable()) {
            val message = appContext.getString(R.string.common_network_unavailable)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            onCompleted(
                InventoryScanLookupResult(
                    quantity = payload.quantity,
                    rawPayload = payload.rawText,
                    errorMessage = message
                )
            )
            return
        }

        viewModelScope.launch {
            val component = lcscCatalogRepository.lookupByPartNumber(payload.partNumber)
            if (component == null) {
                onCompleted(
                    InventoryScanLookupResult(
                        quantity = payload.quantity,
                        rawPayload = payload.rawText,
                        errorMessage = appContext.getString(
                            R.string.inventory_scan_component_not_found,
                            payload.partNumber
                        )
                    )
                )
                return@launch
            }

            onCompleted(
                InventoryScanLookupResult(
                    component = component,
                    quantity = payload.quantity,
                    rawPayload = payload.rawText,
                    existingStockLocations = inventoryRepository.findExistingStockLocations(component.partNumber)
                )
            )
        }
    }

    fun addScannedInbound(
        component: com.example.lcsc_android_erp.domain.model.ComponentDetail,
        quantity: Int,
        locationCode: String,
        rawPayload: String?,
        onCompleted: () -> Unit
    ) {
        if (quantity < 0 || locationCode.isBlank()) {
            return
        }
        viewModelScope.launch {
            inventoryRepository.addInbound(
                com.example.lcsc_android_erp.domain.model.InboundRecord(
                    component = component,
                    quantity = quantity,
                    locationCode = locationCode,
                    sourceType = "LOCATION_SCAN",
                    rawPayload = rawPayload
                )
            )
            onCompleted()
        }
    }

    fun addRecentLocationColor(colorHex: String) {
        viewModelScope.launch {
            userPreferencesRepository.addRecentLocationColor(colorHex)
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InventoryViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    lcscCatalogRepository = appContainer.lcscCatalogRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    appContext = appContainer.appContext
                )
            }
        }
    }

    private fun sortLocationItems(
        items: List<LocationInventoryItem>,
        sortMode: String
    ): List<LocationInventoryItem> {
        val comparator = StorageLocationSortMode.priorities(sortMode)
            .mapNotNull(::priorityComparator)
            .reduceOrNull(::chainComparators)

        return if (comparator == null) {
            items.sortedBy { it.partNumber }
        } else {
            items.sortedWith(chainComparators(comparator, compareBy { it.partNumber }))
        }
    }

    private fun priorityComparator(priority: String): Comparator<LocationInventoryItem>? {
        return when (priority) {
            StorageLocationSortMode.NAME -> {
                compareBy<LocationInventoryItem> { primarySortKey(it) }
            }

            StorageLocationSortMode.QUANTITY -> {
                compareByDescending<LocationInventoryItem> { it.quantity }
            }

            StorageLocationSortMode.INBOUND_TIME -> {
                compareByDescending<LocationInventoryItem> { it.lastInboundAt }
            }

            else -> {
                val specificationKey = StorageLocationSortMode.specificationKey(priority) ?: return null
                compareBy<LocationInventoryItem>(
                    { specificationSortMissing(it, specificationKey) },
                    { specificationSortNumericMissing(it, specificationKey) },
                    { specificationSortNumericValue(it, specificationKey) },
                    { specificationSortTextValue(it, specificationKey) },
                    { primarySortKey(it) }
                )
            }
        }
    }

    private fun <T> chainComparators(
        first: Comparator<T>,
        second: Comparator<T>
    ): Comparator<T> {
        return Comparator { left, right ->
            val result = first.compare(left, right)
            if (result != 0) result else second.compare(left, right)
        }
    }

    private fun specificationSortMissing(item: LocationInventoryItem, specificationKey: String): Boolean {
        return item.specifications[specificationKey]
            ?.trim()
            ?.isEmpty() != false
    }

    private fun specificationSortValue(item: LocationInventoryItem, specificationKey: String): String {
        return item.specifications[specificationKey]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: primarySortKey(item)
    }

    private fun specificationSortNumericMissing(item: LocationInventoryItem, specificationKey: String): Boolean {
        return specificationSortNumericValue(item, specificationKey) == null
    }

    private fun specificationSortNumericValue(item: LocationInventoryItem, specificationKey: String): Double? {
        val rawValue = item.specifications[specificationKey]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return parseEngineeringNumber(rawValue)
    }

    private fun specificationSortTextValue(item: LocationInventoryItem, specificationKey: String): String {
        return specificationSortValue(item, specificationKey)
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun supportedSpecificationKeys(items: List<LocationInventoryItem>): List<String> {
        return items
            .asSequence()
            .flatMap { item -> item.specifications.keys.asSequence() }
            .map(String::trim)
            .distinct()
            .filter { key -> key.isNotEmpty() && hasMultipleSpecificationValues(items, key) }
            .sorted()
            .toList()
    }

    private fun hasMultipleSpecificationValues(
        items: List<LocationInventoryItem>,
        specificationKey: String
    ): Boolean {
        return items
            .asSequence()
            .mapNotNull { item ->
                item.specifications[specificationKey]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .take(2)
            .count() > 1
    }

    private fun primarySortKey(item: LocationInventoryItem): String {
        return item.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.mpn?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.partNumber
    }

    private fun parseEngineeringNumber(rawValue: String): Double? {
        val normalized = rawValue
            .trim()
            .replace("±", "")
            .replace("μ", "u")
            .replace("µ", "u")
            .replace(",", "")
            .takeIf { it.isNotEmpty() }
            ?: return null

        val match = Regex("""^([+-]?\d+(?:\.\d+)?)\s*([pnumkKMGTu]?)(?:[A-Za-z%ΩohmOHM]*)?.*$""")
            .find(normalized)
            ?: return null

        val numericPart = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "p" -> 1e-12
            "n" -> 1e-9
            "u" -> 1e-6
            "m" -> 1e-3
            "k", "K" -> 1e3
            "M" -> 1e6
            "G" -> 1e9
            "T" -> 1e12
            else -> 1.0
        }
        return numericPart * multiplier
    }
}
