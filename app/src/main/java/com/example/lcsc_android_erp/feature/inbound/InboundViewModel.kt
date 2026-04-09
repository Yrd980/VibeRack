package com.example.lcsc_android_erp.feature.inbound

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lcsc_android_erp.core.AppContainer
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.core.network.isNetworkAvailable
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.InboundRecord
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import com.example.lcsc_android_erp.domain.repository.LcscCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboundViewModel(
    private val inventoryRepository: InventoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lcscCatalogRepository: LcscCatalogRepository,
    private val appContext: Context
) : ViewModel() {
    private val inboundState = MutableStateFlow(InboundInternalState())

    val uiState: StateFlow<InboundUiState> = combine(
        inventoryRepository.observeStorageLocations(),
        userPreferencesRepository.preferences,
        inboundState
    ) { locations, preferences, state ->
        InboundUiState(
            defaultLocationCode = preferences.defaultLocationCode,
            locations = locations,
            recentManualSearches = preferences.recentManualSearches,
            manualSearchResults = state.manualSearchResults,
            isSearchingManual = state.isSearchingManual,
            manualSearchError = state.manualSearchError,
            parsedPayload = state.parsedPayload,
            componentDetail = state.componentDetail,
            isLoadingComponent = state.isLoadingComponent,
            componentLookupError = state.componentLookupError,
            lastRawText = state.lastRawText,
            parseError = state.parseError,
            existingStockByPartNumber = state.existingStockByPartNumber
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InboundUiState()
    )

    init {
        viewModelScope.launch {
            inventoryRepository.bootstrapDefaults()
        }
    }

    fun searchManual(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            inboundState.update {
                it.copy(
                    manualSearchResults = emptyList(),
                    isSearchingManual = false,
                    manualSearchError = null
                )
            }
            return
        }

        if (!appContext.isNetworkAvailable()) {
            val message = appContext.getString(R.string.common_network_unavailable)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            inboundState.update {
                it.copy(
                    manualSearchResults = emptyList(),
                    isSearchingManual = false,
                    manualSearchError = message
                )
            }
            return
        }

        inboundState.update {
            it.copy(
                isSearchingManual = true,
                manualSearchError = null
            )
        }

        viewModelScope.launch {
            userPreferencesRepository.addRecentManualSearch(normalized)
            val results = lcscCatalogRepository.searchByKeyword(normalized)
            val existingStocks = results
                .map { it.partNumber }
                .distinct()
                .associateWith { partNumber ->
                    inventoryRepository.findExistingStockLocations(partNumber)
                }
            val sortedResults = results.sortedByDescending { component ->
                existingStocks[component.partNumber].isNullOrEmpty().not()
            }
            inboundState.update {
                it.copy(
                    manualSearchResults = sortedResults,
                    isSearchingManual = false,
                    manualSearchError = if (sortedResults.isEmpty()) {
                        appContext.getString(R.string.inbound_manual_empty)
                    } else {
                        null
                    },
                    existingStockByPartNumber = existingStocks
                )
            }
        }
    }

    fun onQrScanned(rawText: String) {
        val currentState = inboundState.value
        if (currentState.lastRawText == rawText && (currentState.isLoadingComponent || currentState.parsedPayload != null)) {
            return
        }

        val payload = LcscQrParser.parse(rawText)
        if (payload == null) {
            inboundState.update {
                it.copy(
                    parsedPayload = null,
                    componentDetail = null,
                    isLoadingComponent = false,
                    componentLookupError = null,
                    lastRawText = rawText,
                    parseError = appContext.getString(R.string.inbound_scan_invalid_qr)
                )
            }
            return
        }

        if (!appContext.isNetworkAvailable()) {
            val message = appContext.getString(R.string.common_network_unavailable)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            inboundState.update {
                it.copy(
                    parsedPayload = payload,
                    componentDetail = null,
                    isLoadingComponent = false,
                    componentLookupError = message,
                    lastRawText = rawText,
                    parseError = null
                )
            }
            return
        }

        inboundState.update {
            it.copy(
                parsedPayload = payload,
                componentDetail = null,
                isLoadingComponent = true,
                componentLookupError = null,
                lastRawText = rawText,
                parseError = null
            )
        }

        viewModelScope.launch {
            val component = lcscCatalogRepository.lookupByPartNumber(payload.partNumber)
            val existingStocks = component?.let {
                inventoryRepository.findExistingStockLocations(it.partNumber)
            }.orEmpty()
            inboundState.update {
                it.copy(
                    componentDetail = component,
                    isLoadingComponent = false,
                    componentLookupError = if (component == null) {
                        appContext.getString(
                            R.string.inbound_scan_component_not_found,
                            payload.partNumber
                        )
                    } else {
                        null
                    },
                    existingStockByPartNumber = if (component == null) emptyMap() else mapOf(component.partNumber to existingStocks)
                )
            }
        }
    }

    fun clearScanResult() {
        inboundState.update {
            it.copy(
                parsedPayload = null,
                componentDetail = null,
                isLoadingComponent = false,
                componentLookupError = null,
                lastRawText = null,
                parseError = null,
                existingStockByPartNumber = emptyMap()
            )
        }
    }

    fun confirmInbound(
        component: ComponentDetail,
        quantity: Int,
        locationCode: String,
        sourceType: String,
        rawPayload: String? = null
    ) {
        if (quantity < 0 || locationCode.isBlank()) {
            return
        }
        viewModelScope.launch {
            inventoryRepository.addInbound(
                InboundRecord(
                    component = component,
                    quantity = quantity,
                    locationCode = locationCode,
                    sourceType = sourceType,
                    rawPayload = rawPayload
                )
            )
            val existingStocks = inventoryRepository.findExistingStockLocations(component.partNumber)
            inboundState.update {
                it.copy(
                    existingStockByPartNumber = it.existingStockByPartNumber + (component.partNumber to existingStocks)
                )
            }
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InboundViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    lcscCatalogRepository = appContainer.lcscCatalogRepository,
                    appContext = appContainer.appContext
                )
            }
        }
    }
}

private data class InboundInternalState(
    val manualSearchResults: List<ComponentDetail> = emptyList(),
    val isSearchingManual: Boolean = false,
    val manualSearchError: String? = null,
    val parsedPayload: InboundQrPayload? = null,
    val componentDetail: ComponentDetail? = null,
    val isLoadingComponent: Boolean = false,
    val componentLookupError: String? = null,
    val lastRawText: String? = null,
    val parseError: String? = null,
    val existingStockByPartNumber: Map<String, List<ExistingStockLocation>> = emptyMap()
)
