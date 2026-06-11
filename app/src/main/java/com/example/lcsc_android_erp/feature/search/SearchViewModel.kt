package com.example.lcsc_android_erp.feature.search

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lcsc_android_erp.core.AppContainer
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisOperations
import com.example.lcsc_android_erp.core.network.isNetworkAvailable
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.InboundRecord
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import com.example.lcsc_android_erp.domain.repository.LcscCatalogRepository
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val inventoryRepository: InventoryRepository,
    private val boxRepository: BoxRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lcscCatalogRepository: LcscCatalogRepository,
    private val smartChassisOperations: SmartChassisOperations,
    private val appContext: Context
) : ViewModel() {
    private data class BomBindingContext(
        val records: List<SearchInventoryRecord>,
        val persistentBindings: Map<String, String>,
        val temporaryBindings: Map<String, String>,
        val ignoredEntryKeys: Set<String>,
        val boxLayers: List<ComponentBoxLayer>
    )

    private data class BomPickState(
        val session: BomPickSessionUiModel?,
        val message: String?,
        val isBusy: Boolean
    )

    private data class BomScreenStatus(
        val errorMessage: String?,
        val isParsing: Boolean
    )

    private val inventoryRecords = inventoryRepository.observeSearchInventoryRecords()
    private val allBoxLayers = boxRepository.observeAllLayers()
    private val emptyBoxLayers = boxRepository.observeEmptyLayers()
    private val locations = inventoryRepository.observeStorageLocations()
    private val persistentBomBindings = userPreferencesRepository.preferences.map { it.bomPartBindings }
    private val defaultLocationCode = userPreferencesRepository.preferences.map { it.defaultLocationCode }
    private val mode = MutableStateFlow(SearchMode.Manual)
    private val query = MutableStateFlow("")
    private val currentPage = MutableStateFlow(1)
    private val parsedBomDocument = MutableStateFlow<ParsedBomDocument?>(null)
    private val bomFilter = MutableStateFlow(BomMatchFilter.All)
    private val temporaryBomBindings = MutableStateFlow<Map<String, String>>(emptyMap())
    private val ignoredBomEntryKeys = MutableStateFlow<Set<String>>(emptySet())
    private val bomError = MutableStateFlow<String?>(null)
    private val isParsingBom = MutableStateFlow(false)
    private val bomPickSession = MutableStateFlow<BomPickSessionUiModel?>(null)
    private val bomPickMessage = MutableStateFlow<String?>(null)
    private val isBomPickBusy = MutableStateFlow(false)
    private val bomPickState = combine(
        bomPickSession,
        bomPickMessage,
        isBomPickBusy
    ) { session, pickMessage, pickBusy ->
        BomPickState(
            session = session,
            message = pickMessage,
            isBusy = pickBusy
        )
    }
    private val bomScreenStatus = combine(
        bomError,
        isParsingBom
    ) { errorMessage, parsingBom ->
        BomScreenStatus(
            errorMessage = errorMessage,
            isParsing = parsingBom
        )
    }

    private val bomBindingContext = combine(
        inventoryRecords,
        persistentBomBindings,
        temporaryBomBindings,
        ignoredBomEntryKeys,
        allBoxLayers
    ) { records, persistentBindings, temporaryBindings, ignoredEntryKeys, layers ->
        BomBindingContext(
            records = records,
            persistentBindings = persistentBindings,
            temporaryBindings = temporaryBindings,
            ignoredEntryKeys = ignoredEntryKeys,
            boxLayers = layers
        )
    }

    private val baseUiStateCore = combine(
        bomBindingContext,
        mode,
        query,
        currentPage,
        parsedBomDocument
    ) { bindingContext, searchMode, queryText, page, bomDocument ->
        val allInventoryResults = SearchInventoryWorkflow.groupRecords(bindingContext.records)
        val filteredRecords = SearchInventoryWorkflow.filterRecords(bindingContext.records, queryText)
        val groupedResults = SearchInventoryWorkflow.groupRecords(filteredRecords)
        val pageCount = maxOf(1, (groupedResults.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val safePage = page.coerceIn(1, pageCount)
        val allBomRows = BomWorkflow.buildRows(
            inventoryRecords = bindingContext.records,
            document = bomDocument,
            persistentBindings = bindingContext.persistentBindings,
            temporaryBindings = bindingContext.temporaryBindings,
            ignoredEntryKeys = bindingContext.ignoredEntryKeys,
            boxLayers = bindingContext.boxLayers
        )
        SearchUiState(
            mode = searchMode,
            query = queryText,
            allInventoryResults = allInventoryResults,
            results = groupedResults,
            pagedResults = groupedResults.take(safePage * PAGE_SIZE),
            inventoryRecordCount = bindingContext.records.size,
            currentPage = safePage,
            pageCount = pageCount,
            bomFileName = bomDocument?.fileName,
            bomRows = allBomRows,
            bomMatchedCount = allBomRows.count { it.matchedResults.isNotEmpty() || it.assignedLayers.isNotEmpty() }
        )
    }

    private val baseUiState = combine(
        baseUiStateCore,
        bomFilter
    ) { baseState, currentBomFilter ->
        baseState.copy(
            bomFilter = currentBomFilter,
            bomRows = baseState.bomRows.filter { row ->
                when (currentBomFilter) {
                    BomMatchFilter.All -> true
                    BomMatchFilter.Matched -> row.matchedResults.isNotEmpty() || row.assignedLayers.isNotEmpty()
                    BomMatchFilter.Unmatched -> row.matchedResults.isEmpty() && row.assignedLayers.isEmpty()
                }
            }
        )
    }

    private val baseUiStateWithEmptyLayers = combine(
        baseUiState,
        emptyBoxLayers
    ) { baseState, emptyLayers ->
        baseState.copy(emptyBoxLayers = emptyLayers)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        baseUiStateWithEmptyLayers,
        bomScreenStatus,
        locations,
        defaultLocationCode,
        bomPickState
    ) { baseState, bomStatus, storageLocations, defaultCode, pickState ->
        baseState.copy(
            defaultLocationCode = defaultCode,
            locations = storageLocations,
            bomError = bomStatus.errorMessage,
            isParsingBom = bomStatus.isParsing,
            bomPickSession = pickState.session,
            bomPickMessage = pickState.message,
            isBomPickBusy = pickState.isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun updateMode(value: SearchMode) {
        mode.update { value }
    }

    fun updateQuery(value: String) {
        currentPage.update { 1 }
        query.update { value }
    }

    fun startBomImport() {
        isParsingBom.value = true
        bomError.value = null
    }

    fun onBomImportSuccess(document: ParsedBomDocument) {
        parsedBomDocument.value = document
        bomFilter.value = BomMatchFilter.All
        temporaryBomBindings.value = emptyMap()
        ignoredBomEntryKeys.value = emptySet()
        bomPickSession.value = null
        bomPickMessage.value = null
        bomError.value = null
        isParsingBom.value = false
    }

    fun onBomImportFailed(message: String) {
        bomError.value = message
        isParsingBom.value = false
    }

    fun updateBomFilter(filter: BomMatchFilter) {
        bomFilter.value = filter
    }

    fun ignoreBomEntry(entry: BomSearchEntry) {
        ignoredBomEntryKeys.update { current -> current + BomWorkflow.entryKey(entry) }
    }

    fun bindBomEntry(
        entry: BomSearchEntry,
        localPartNumber: String
    ) {
        val normalizedPartNumber = localPartNumber.trim().uppercase(Locale.ROOT)
        if (normalizedPartNumber.isBlank()) {
            return
        }

        val supplierPart = entry.supplierPart?.trim()?.uppercase(Locale.ROOT)
        if (!supplierPart.isNullOrEmpty()) {
            viewModelScope.launch {
                userPreferencesRepository.setBomPartBinding(supplierPart, normalizedPartNumber)
            }
        } else {
            temporaryBomBindings.update { current ->
                current + (BomWorkflow.entryKey(entry) to normalizedPartNumber)
            }
        }
        ignoredBomEntryKeys.update { current -> current - BomWorkflow.entryKey(entry) }
    }

    fun lookupBomDirectInbound(
        partNumber: String,
        onCompleted: (BomDirectInboundLookupResult) -> Unit
    ) {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        if (normalizedPartNumber.isBlank()) {
            onCompleted(
                BomDirectInboundLookupResult(
                    errorMessage = appContext.getString(R.string.search_bom_direct_inbound_no_part)
                )
            )
            return
        }

        if (!appContext.isNetworkAvailable()) {
            val message = appContext.getString(R.string.common_network_unavailable)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            onCompleted(
                BomDirectInboundLookupResult(
                    errorMessage = message
                )
            )
            return
        }

        viewModelScope.launch {
            val component = lcscCatalogRepository.lookupByPartNumber(normalizedPartNumber)
            if (component == null) {
                onCompleted(
                    BomDirectInboundLookupResult(
                        errorMessage = appContext.getString(
                            R.string.search_bom_direct_inbound_not_found,
                            normalizedPartNumber
                        )
                    )
                )
                return@launch
            }
            onCompleted(
                BomDirectInboundLookupResult(
                    component = component,
                    existingStockLocations = inventoryRepository.findExistingStockLocations(component.partNumber)
                )
            )
        }
    }

    fun addBomInbound(
        component: com.example.lcsc_android_erp.domain.model.ComponentDetail,
        quantity: Int,
        locationCode: String,
        onCompleted: (String?) -> Unit
    ) {
        val normalizedLocationCode = locationCode.trim().uppercase(Locale.ROOT)
        if (quantity < 0 || normalizedLocationCode.isBlank()) {
            onCompleted(appContext.getString(R.string.search_bom_direct_inbound_invalid_input))
            return
        }

        viewModelScope.launch {
            inventoryRepository.addInbound(
                InboundRecord(
                    component = component,
                    quantity = quantity,
                    locationCode = normalizedLocationCode,
                    sourceType = "BOM"
                )
            )
            onCompleted(null)
        }
    }

    fun assignBomEntryToEmptyLayer(
        entry: BomSearchEntry,
        onCompleted: (BomLayerAssignmentResult) -> Unit
    ) {
        val normalizedPartNumber = entry.supplierPart?.trim()?.uppercase(Locale.ROOT)
        if (normalizedPartNumber.isNullOrBlank()) {
            onCompleted(
                BomLayerAssignmentResult(
                    errorMessage = appContext.getString(R.string.search_bom_assign_no_part)
                )
            )
            return
        }

        if (!appContext.isNetworkAvailable()) {
            val message = appContext.getString(R.string.common_network_unavailable)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            onCompleted(BomLayerAssignmentResult(errorMessage = message))
            return
        }

        viewModelScope.launch {
            val component = lcscCatalogRepository.lookupByPartNumber(normalizedPartNumber)
            if (component == null) {
                onCompleted(
                    BomLayerAssignmentResult(
                        errorMessage = appContext.getString(
                            R.string.search_bom_direct_inbound_not_found,
                            normalizedPartNumber
                        )
                    )
                )
                return@launch
            }

            val assignedLayer = boxRepository.assignComponentToFirstEmptyLayer(
                component = component,
                quantity = entry.quantity?.coerceAtLeast(0) ?: 0,
                sourceType = "BOM",
                rawPayload = BomWorkflow.rawPayload(entry)
            )
            if (assignedLayer == null) {
                onCompleted(
                    BomLayerAssignmentResult(
                        partNumber = component.partNumber,
                        errorMessage = appContext.getString(R.string.search_bom_assign_no_empty_layer)
                    )
                )
            } else {
                onCompleted(
                    BomLayerAssignmentResult(
                        assignedLayer = assignedLayer,
                        partNumber = component.partNumber
                    )
                )
            }
        }
    }

    fun goToNextPage() {
        currentPage.update { page -> page + 1 }
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

    fun deleteInventoryItem(
        inventoryItemId: Long,
        onCompleted: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onCompleted(inventoryRepository.deleteInventoryItem(inventoryItemId))
        }
    }

    fun startBomPickToLight() {
        val session = BomWorkflow.buildPickSession(uiState.value.bomRows)
        if (session == null || session.groups.isEmpty()) {
            bomPickMessage.value = appContext.getString(R.string.search_bom_pick_no_targets)
            return
        }
        viewModelScope.launch {
            isBomPickBusy.value = true
            bomPickMessage.value = null
            val failedGroup = session.groups.firstOrNull { group ->
                !sendPickMask(group)
            }
            isBomPickBusy.value = false
            if (failedGroup == null) {
                bomPickSession.value = session
                bomPickMessage.value = appContext.getString(
                    R.string.search_bom_pick_started,
                    session.slotCount,
                    session.groups.size
                )
            } else {
                bomPickMessage.value = smartChassisOperations.lastOperationError.value?.message
                    ?: appContext.getString(R.string.search_bom_pick_failed, failedGroup.containerCode)
            }
        }
    }

    fun cancelBomPickToLight() {
        val session = bomPickSession.value ?: return
        viewModelScope.launch {
            isBomPickBusy.value = true
            session.groups.forEach { group ->
                smartChassisOperations.lightsOff(group.macAddress)
            }
            bomPickSession.value = null
            bomPickMessage.value = appContext.getString(R.string.search_bom_pick_cancelled)
            isBomPickBusy.value = false
        }
    }

    fun findSmartChassisRecord(
        record: SearchInventoryRecord,
        onCompleted: (String?) -> Unit
    ) {
        val slotNumber = record.slotNumber ?: 0
        val macAddress = record.containerMacAddress?.trim()?.uppercase(Locale.ROOT)
        if (record.containerType != ContainerType.SMART_CHASSIS ||
            macAddress.isNullOrBlank() ||
            slotNumber !in 1..25
        ) {
            onCompleted(appContext.getString(R.string.search_find_light_unavailable))
            return
        }

        viewModelScope.launch {
            onCompleted(
                if (!smartChassisOperations.findSlot(macAddress, slotNumber)) {
                    smartChassisOperations.lastOperationError.value?.message
                        ?: appContext.getString(R.string.search_find_light_failed)
                } else {
                    null
                }
            )
        }
    }

    private suspend fun sendPickMask(group: BomPickGroupUiModel): Boolean {
        return smartChassisOperations.pickSlots(group.macAddress, group.slots)
    }

    companion object {
        private const val PAGE_SIZE = 10

        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    boxRepository = appContainer.boxRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    lcscCatalogRepository = appContainer.lcscCatalogRepository,
                    smartChassisOperations = appContainer.smartChassisOperations,
                    appContext = appContainer.appContext
                )
            }
        }
    }

}
