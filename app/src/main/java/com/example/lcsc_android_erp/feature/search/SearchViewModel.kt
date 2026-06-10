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
import com.example.lcsc_android_erp.core.network.isNetworkAvailable
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
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
    private val appContext: Context
) : ViewModel() {
    private data class BomBindingContext(
        val records: List<SearchInventoryRecord>,
        val persistentBindings: Map<String, String>,
        val temporaryBindings: Map<String, String>,
        val ignoredEntryKeys: Set<String>,
        val boxLayers: List<ComponentBoxLayer>
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
        val allInventoryResults = groupRecords(bindingContext.records)
        val filteredRecords = filterRecords(bindingContext.records, queryText)
        val groupedResults = groupRecords(filteredRecords)
        val pageCount = maxOf(1, (groupedResults.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val safePage = page.coerceIn(1, pageCount)
        val allBomRows = buildBomRows(
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
        bomError,
        isParsingBom,
        locations,
        defaultLocationCode
    ) { baseState, bomErrorMessage, parsingBom, storageLocations, defaultCode ->
        baseState.copy(
            defaultLocationCode = defaultCode,
            locations = storageLocations,
            bomError = bomErrorMessage,
            isParsingBom = parsingBom
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
        ignoredBomEntryKeys.update { current -> current + bomEntryKey(entry) }
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
                current + (bomEntryKey(entry) to normalizedPartNumber)
            }
        }
        ignoredBomEntryKeys.update { current -> current - bomEntryKey(entry) }
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
                rawPayload = entry.toRawPayload()
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

    companion object {
        private const val PAGE_SIZE = 10

        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    boxRepository = appContainer.boxRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    lcscCatalogRepository = appContainer.lcscCatalogRepository,
                    appContext = appContainer.appContext
                )
            }
        }
    }

    private fun filterRecords(
        records: List<SearchInventoryRecord>,
        queryText: String
    ): List<SearchInventoryRecord> {
        val normalizedQuery = queryText.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            return records
        }

        return records.filter { record ->
            buildSearchTokens(record).any { token ->
                token.contains(normalizedQuery)
            }
        }
    }

    private fun buildSearchTokens(record: SearchInventoryRecord): List<String> {
        return buildList {
            add(record.partNumber)
            record.mpn?.let(::add)
            record.name?.let(::add)
            record.brand?.let(::add)
            record.packageName?.let(::add)
            record.category?.let(::add)
            record.description?.let(::add)
            add(record.locationCode)
            record.locationDisplayName?.let(::add)
            record.specifications.forEach { (key, value) ->
                add(key)
                add(value)
            }
        }.mapNotNull { value ->
            value.trim()
                .takeIf { it.isNotEmpty() }
                ?.lowercase(Locale.ROOT)
        }
    }

    private fun groupRecords(records: List<SearchInventoryRecord>): List<SearchResultUiModel> {
        return records
            .groupBy { "${it.partNumber}|${it.mpn.orEmpty()}" }
            .values
            .map { group ->
                val first = group.first()
                SearchResultUiModel(
                    partNumber = first.partNumber,
                    mpn = first.mpn,
                    name = first.name,
                    brand = first.brand,
                    packageName = first.packageName,
                    category = first.category,
                    description = first.description,
                    sourceUrl = first.sourceUrl,
                    specifications = first.specifications,
                    imageLocalPath = first.imageLocalPath,
                    totalQuantity = group.sumOf { it.quantity },
                    locations = group
                        .sortedBy { it.locationCode }
                        .map { record ->
                            SearchResultLocationUiModel(
                                code = record.locationCode,
                                displayName = record.locationDisplayName,
                                colorHex = record.locationColorHex,
                                quantity = record.quantity
                            )
                        },
                    records = group.sortedBy { it.locationCode }
                )
            }
            .sortedWith(
                compareBy<SearchResultUiModel> {
                    it.name?.trim()?.takeIf(String::isNotEmpty)
                        ?: it.mpn?.trim()?.takeIf(String::isNotEmpty)
                        ?: it.partNumber
                }.thenBy { it.partNumber }
            )
    }

    private fun buildBomRows(
        inventoryRecords: List<SearchInventoryRecord>,
        document: ParsedBomDocument?,
        persistentBindings: Map<String, String>,
        temporaryBindings: Map<String, String>,
        ignoredEntryKeys: Set<String>,
        boxLayers: List<ComponentBoxLayer>
    ): List<BomSearchRowUiModel> {
        if (document == null) {
            return emptyList()
        }

        return document.entries.map { entry ->
            val entryKey = bomEntryKey(entry)
            if (entryKey in ignoredEntryKeys) {
                null
            } else {
                val persistentBindingPartNumber = entry.supplierPart
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.let(persistentBindings::get)
                val temporaryBindingPartNumber = temporaryBindings[entryKey]
                val boundPartNumber = persistentBindingPartNumber ?: temporaryBindingPartNumber
                val resolvedPartNumber = boundPartNumber
                    ?: entry.supplierPart
                        ?.trim()
                        ?.uppercase(Locale.ROOT)
                        ?.takeIf { it.isNotBlank() }
                val matchedRecords = inventoryRecords.filter { record ->
                    matchesBomEntry(
                        record = record,
                        entry = entry,
                        boundPartNumber = boundPartNumber
                    )
                }
                val assignedLayers = resolvedPartNumber?.let { partNumber ->
                    boxLayers.filter { layer ->
                        layer.partNumber?.trim()?.uppercase(Locale.ROOT) == partNumber
                    }
                }.orEmpty()
                BomSearchRowUiModel(
                    entry = entry,
                    matchedResults = groupRecords(matchedRecords),
                    assignedLayers = assignedLayers,
                    isBound = boundPartNumber != null,
                    isPersistentBinding = persistentBindingPartNumber != null
                )
            }
        }.filterNotNull()
    }

    private fun matchesBomEntry(
        record: SearchInventoryRecord,
        entry: BomSearchEntry,
        boundPartNumber: String?
    ): Boolean {
        if (!boundPartNumber.isNullOrBlank()) {
            return record.partNumber.trim().uppercase(Locale.ROOT) == boundPartNumber
        }
        val supplierPart = entry.supplierPart?.trim()?.uppercase(Locale.ROOT)
        if (!supplierPart.isNullOrEmpty()) {
            return record.partNumber.trim().uppercase(Locale.ROOT) == supplierPart
        }

        val manufacturerPart = entry.manufacturerPart?.trim()?.uppercase(Locale.ROOT)
        if (!manufacturerPart.isNullOrEmpty()) {
            val mpn = record.mpn?.trim()?.uppercase(Locale.ROOT)
            return mpn == manufacturerPart
        }

        return matchesPassiveFootprintEntry(record, entry)
    }

    private fun matchesPassiveFootprintEntry(
        record: SearchInventoryRecord,
        entry: BomSearchEntry
    ): Boolean {
        val footprint = entry.footprint?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val passiveType = parsePassiveFootprintType(footprint) ?: return false
        val normalizedPackage = parsePassiveFootprintPackage(footprint) ?: return false
        val recordPackage = normalizePackageName(record.packageName) ?: return false
        if (recordPackage != normalizedPackage) {
            return false
        }

        val category = record.category?.trim().orEmpty()
        val matchesCategory = when (passiveType) {
            PassiveFootprintType.Resistor -> category.contains("电阻", ignoreCase = true)
            PassiveFootprintType.Capacitor -> category.contains("电容", ignoreCase = true)
        }
        if (!matchesCategory) {
            return false
        }

        val bomValue = normalizePassiveValue(
            entry.value?.takeIf { it.isNotBlank() } ?: entry.comment.orEmpty()
        )
        if (bomValue.isEmpty()) {
            return false
        }

        val specificationKeys = when (passiveType) {
            PassiveFootprintType.Resistor -> listOf("阻值")
            PassiveFootprintType.Capacitor -> listOf("容值")
        }

        val recordValue = specificationKeys.asSequence()
            .mapNotNull { key -> record.specifications[key] }
            .map(::normalizePassiveValue)
            .firstOrNull { it.isNotEmpty() }
            ?: return false

        return recordValue == bomValue
    }

    private fun parsePassiveFootprintType(footprint: String): PassiveFootprintType? {
        return when {
            footprint.matches(Regex("^R\\d{4}$")) -> PassiveFootprintType.Resistor
            footprint.matches(Regex("^C\\d{4}$")) -> PassiveFootprintType.Capacitor
            else -> null
        }
    }

    private fun parsePassiveFootprintPackage(footprint: String): String? {
        return footprint.drop(1).takeIf { it.length == 4 && it.all(Char::isDigit) }
    }

    private fun normalizePackageName(packageName: String?): String? {
        val normalized = packageName?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val match = Regex("(\\d{4})").find(normalized)
        return match?.groupValues?.getOrNull(1)
    }

    private fun normalizePassiveValue(value: String): String {
        return value
            .trim()
            .uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("Ω", "")
            .replace("OHM", "")
            .replace("欧姆", "")
            .replace("µ", "U")
            .replace("μ", "U")
    }

    private enum class PassiveFootprintType {
        Resistor,
        Capacitor
    }

    private fun bomEntryKey(entry: BomSearchEntry): String {
        return listOf(
            entry.rowNumber,
            entry.supplierPart.orEmpty(),
            entry.manufacturerPart.orEmpty(),
            entry.designator.orEmpty(),
            entry.value.orEmpty()
        ).joinToString("|") { it.trim().uppercase(Locale.ROOT) }
    }

    private fun BomSearchEntry.toRawPayload(): String {
        return listOf(
            "row=$rowNumber",
            "quantity=${quantity ?: 0}",
            "supplierPart=${supplierPart.orEmpty()}",
            "manufacturerPart=${manufacturerPart.orEmpty()}",
            "manufacturer=${manufacturer.orEmpty()}",
            "value=${value.orEmpty()}",
            "footprint=${footprint.orEmpty()}",
            "designator=${designator.orEmpty()}"
        ).joinToString(";")
    }
}
