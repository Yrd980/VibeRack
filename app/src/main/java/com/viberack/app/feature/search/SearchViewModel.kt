package com.viberack.app.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.viberack.app.R
import com.viberack.app.core.AppContainer
import com.viberack.app.core.ble.smart.GuidanceTarget
import com.viberack.app.core.ble.smart.PhysicalGuidance
import com.viberack.app.core.ble.smart.PickGroup
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.repository.ContainerRepository
import com.viberack.app.domain.repository.InventoryRepository
import com.viberack.app.domain.repository.SlotOperationRepository
import com.viberack.app.domain.repository.SlotOperationWrite
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val inventoryRepository: InventoryRepository,
    private val containerRepository: ContainerRepository,
    private val slotOperationRepository: SlotOperationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val physicalGuidance: PhysicalGuidance,
    private val appContext: Context
) : ViewModel() {
    private data class BomBindingContext(
        val records: List<SearchInventoryRecord>,
        val persistentBindings: Map<String, String>,
        val temporaryBindings: Map<String, String>,
        val ignoredEntryKeys: Set<String>
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

    private data class SmartSlotInboundState(
        val targets: List<SmartSlotInboundTargetUiModel>,
        val message: String?,
        val isBusy: Boolean
    )

    private val inventoryRecords = inventoryRepository.observeSearchInventoryRecords()
    private val persistentBomBindings = userPreferencesRepository.preferences.map { it.bomPartBindings }
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
    private val smartSlotInboundMessage = MutableStateFlow<String?>(null)
    private val isSmartSlotInboundBusy = MutableStateFlow(false)

    private val smartSlotInboundTargets = containerRepository.observeContainers()
        .map { containers ->
            containers
                .filter { container ->
                    container.type == ContainerType.SMART_CHASSIS &&
                        !container.macAddress.isNullOrBlank() &&
                        container.slotCount >= 1
                }
        }
        .flatMapLatest { smartContainers ->
            if (smartContainers.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    smartContainers.map { container ->
                        containerRepository.observeContainerSlotStock(container.id).map { slots ->
                            container to slots
                        }
                    }
                ) { pairs ->
                    pairs.flatMap { (container, slots) ->
                        slots
                            .asSequence()
                            .filter { slot -> slot.stockItem == null }
                            .filter { slot -> slot.slot.slotNumber in 1..25 }
                            .map { slot ->
                                SmartSlotInboundTargetUiModel(
                                    containerId = container.id,
                                    containerCode = container.code,
                                    containerName = container.displayName,
                                    macAddress = container.macAddress.orEmpty(),
                                    slotNumber = slot.slot.slotNumber,
                                    slotCode = slot.slot.slotCode
                                )
                            }
                            .toList()
                    }.sortedWith(
                        compareBy<SmartSlotInboundTargetUiModel> { it.containerCode }
                            .thenBy { it.slotNumber }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val smartSlotInboundState = combine(
        smartSlotInboundTargets,
        smartSlotInboundMessage,
        isSmartSlotInboundBusy
    ) { targets, inboundMessage, inboundBusy ->
        SmartSlotInboundState(
            targets = targets,
            message = inboundMessage,
            isBusy = inboundBusy
        )
    }

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
        ignoredBomEntryKeys
    ) { records, persistentBindings, temporaryBindings, ignoredEntryKeys ->
        BomBindingContext(
            records = records,
            persistentBindings = persistentBindings,
            temporaryBindings = temporaryBindings,
            ignoredEntryKeys = ignoredEntryKeys
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
            ignoredEntryKeys = bindingContext.ignoredEntryKeys
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
            bomMatchedCount = allBomRows.count { it.matchedResults.isNotEmpty() }
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
                    BomMatchFilter.Matched -> row.matchedResults.isNotEmpty()
                    BomMatchFilter.Unmatched -> row.matchedResults.isEmpty()
                }
            }
        )
    }

    val uiState: StateFlow<SearchUiState> = combine(
        baseUiState,
        bomScreenStatus,
        bomPickState,
        smartSlotInboundState
    ) { baseState, bomStatus, pickState, inboundState ->
        baseState.copy(
            bomError = bomStatus.errorMessage,
            isParsingBom = bomStatus.isParsing,
            bomPickSession = pickState.session,
            bomPickMessage = pickState.message,
            isBomPickBusy = pickState.isBusy,
            smartSlotInboundTargets = inboundState.targets,
            smartSlotInboundMessage = inboundState.message,
            isSmartSlotInboundBusy = inboundState.isBusy
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

    fun goToNextPage() {
        currentPage.update { page -> page + 1 }
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
            val failedGroup = physicalGuidance.pickGroups(
                session.groups.map { group -> PickGroup(group.macAddress, group.slots) }
            )
            isBomPickBusy.value = false
            if (failedGroup == null) {
                bomPickSession.value = session
                bomPickMessage.value = appContext.getString(
                    R.string.search_bom_pick_started,
                    session.slotCount,
                    session.groups.size
                )
            } else {
                bomPickMessage.value = physicalGuidance.lastOperationError.value?.message
                    ?: appContext.getString(
                        R.string.search_bom_pick_failed,
                        session.groups.firstOrNull { it.macAddress == failedGroup.macAddress }?.containerCode.orEmpty()
                    )
            }
        }
    }

    fun cancelBomPickToLight() {
        val session = bomPickSession.value ?: return
        viewModelScope.launch {
            isBomPickBusy.value = true
            session.groups.forEach { group ->
                physicalGuidance.lightsOff(group.macAddress)
            }
            bomPickSession.value = null
            bomPickMessage.value = appContext.getString(R.string.search_bom_pick_cancelled)
            isBomPickBusy.value = false
        }
    }

    fun bindRecordToSmartSlot(
        record: SearchInventoryRecord,
        target: SmartSlotInboundTargetUiModel,
        quantity: Int,
        onCompleted: (String?) -> Unit
    ) {
        if (quantity < 0 || target.slotNumber !in 1..25 || target.macAddress.isBlank()) {
            onCompleted(appContext.getString(R.string.search_smart_slot_inbound_invalid))
            return
        }
        viewModelScope.launch {
            isSmartSlotInboundBusy.value = true
            smartSlotInboundMessage.value = null
            val lit = physicalGuidance.guideStockIn(GuidanceTarget(target.macAddress, target.slotNumber))
            if (!lit) {
                val message = physicalGuidance.lastOperationError.value?.message
                    ?: appContext.getString(R.string.search_smart_slot_inbound_light_failed)
                smartSlotInboundMessage.value = message
                isSmartSlotInboundBusy.value = false
                onCompleted(message)
                return@launch
            }
            val result = slotOperationRepository.writeOne(
                SlotOperationWrite(
                    containerId = target.containerId,
                    slotNumber = target.slotNumber,
                    component = record.toComponentDetail(),
                    quantity = quantity,
                    sourceType = SOURCE_SEARCH_SMART_SLOT_INBOUND,
                    rawPayload = "slot=${target.slotNumber};part=${record.partNumber};source=search"
                )
            )
            isSmartSlotInboundBusy.value = false
            if (result.success) {
                val message = appContext.getString(
                    R.string.search_smart_slot_inbound_success,
                    record.partNumber,
                    target.containerCode,
                    target.slotCode
                )
                smartSlotInboundMessage.value = message
                onCompleted(null)
            } else {
                val message = result.message
                    ?: appContext.getString(R.string.search_smart_slot_inbound_failed)
                smartSlotInboundMessage.value = message
                onCompleted(message)
            }
        }
    }

    fun findSmartChassisRecord(
        record: SearchInventoryRecord,
        onCompleted: (String?) -> Unit
    ) {
        if (!record.canFindByLight) {
            onCompleted(appContext.getString(R.string.search_find_light_unavailable))
            return
        }

        viewModelScope.launch {
            onCompleted(
                if (!physicalGuidance.findRecord(record)) {
                    physicalGuidance.lastOperationError.value?.message
                        ?: appContext.getString(R.string.search_find_light_failed)
                } else {
                    null
                }
            )
        }
    }

    companion object {
        private const val PAGE_SIZE = 10

        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    containerRepository = appContainer.containerRepository,
                    slotOperationRepository = appContainer.slotOperationRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    physicalGuidance = appContainer.physicalGuidance,
                    appContext = appContainer.appContext
                )
            }
        }
    }
}

private fun SearchInventoryRecord.toComponentDetail(): ComponentDetail {
    return ComponentDetail(
        partNumber = partNumber,
        mpn = mpn,
        name = name,
        brand = brand,
        packageName = packageName,
        category = category,
        description = description,
        stockQuantity = null,
        price = null,
        productUrl = sourceUrl,
        datasheetUrl = null,
        imageLocalPath = imageLocalPath,
        imageUrl = null,
        specifications = specifications
    )
}

private const val SOURCE_SEARCH_SMART_SLOT_INBOUND = "SEARCH_SMART_SLOT_INBOUND"
