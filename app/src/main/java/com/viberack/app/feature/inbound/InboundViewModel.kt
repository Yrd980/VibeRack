package com.viberack.app.feature.inbound

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.viberack.app.core.AppContainer
import com.viberack.app.R
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.core.network.isNetworkAvailable
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.InboundRecord
import com.viberack.app.domain.repository.InventoryRepository
import com.viberack.app.domain.repository.LcscCatalogRepository
import java.util.Locale
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
        inventoryRepository.observeLocationCategoryProfiles(),
        userPreferencesRepository.preferences,
        inboundState
    ) { locations, locationCategoryProfiles, preferences, state ->
        InboundUiState(
            defaultLocationCode = preferences.defaultLocationCode,
            nextManualInboundPartNumber = state.nextManualInboundPartNumber,
            locations = locations,
            locationCategoryProfiles = locationCategoryProfiles,
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
            inventoryRepository.refreshMissingLocationCategoryProfiles()
            refreshNextManualInboundPartNumberInternal()
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
            runCatching {
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
            }.onFailure { throwable ->
                Log.w(TAG, "searchManual failed keyword=$normalized", throwable)
                inboundState.update {
                    it.copy(
                        manualSearchResults = emptyList(),
                        isSearchingManual = false,
                        manualSearchError = appContext.getString(R.string.inbound_manual_empty)
                    )
                }
            }
        }
    }

    fun refreshExistingStock(partNumber: String) {
        val normalizedPartNumber = partNumber.trim().uppercase()
        if (normalizedPartNumber.isBlank()) {
            inboundState.update {
                it.copy(
                    existingStockByPartNumber = it.existingStockByPartNumber - normalizedPartNumber
                )
            }
            return
        }
        viewModelScope.launch {
            val existingStocks = inventoryRepository.findExistingStockLocations(normalizedPartNumber)
            inboundState.update {
                it.copy(
                    existingStockByPartNumber = it.existingStockByPartNumber + (normalizedPartNumber to existingStocks)
                )
            }
        }
    }

    fun refreshNextManualInboundPartNumber() {
        Log.d(TAG, "refreshNextManualInboundPartNumber requested")
        viewModelScope.launch {
            refreshNextManualInboundPartNumberInternal()
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
            runCatching {
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
            }.onFailure { throwable ->
                Log.w(TAG, "onQrScanned lookup failed partNumber=${payload.partNumber}", throwable)
                inboundState.update {
                    it.copy(
                        componentDetail = null,
                        isLoadingComponent = false,
                        componentLookupError = appContext.getString(
                            R.string.inbound_scan_component_not_found,
                            payload.partNumber
                        ),
                        existingStockByPartNumber = emptyMap()
                    )
                }
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
        rawPayload: String? = null,
        onCompleted: (String?) -> Unit = {}
    ) {
        val normalizedLocationCode = locationCode.trim().uppercase(Locale.ROOT)
        if (quantity < 0 || normalizedLocationCode.isBlank()) {
            onCompleted(appContext.getString(R.string.inbound_invalid_input))
            return
        }
        viewModelScope.launch {
            runCatching {
                inventoryRepository.addInbound(
                    InboundRecord(
                        component = component,
                        quantity = quantity,
                        locationCode = normalizedLocationCode,
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
                if (sourceType == "MANUAL_INPUT") {
                    refreshNextManualInboundPartNumberInternal()
                }
                onCompleted(null)
            }.onFailure { throwable ->
                Log.e(TAG, "confirmInbound failed partNumber=${component.partNumber}", throwable)
                onCompleted(throwable.message ?: appContext.getString(R.string.inbound_invalid_input))
            }
        }
    }

    private suspend fun refreshNextManualInboundPartNumberInternal() {
        runCatching {
            val nextPartNumber = inventoryRepository.getNextManualInboundPartNumber()
            Log.d(TAG, "refreshNextManualInboundPartNumberInternal resolved nextPartNumber=$nextPartNumber")
            inboundState.update {
                it.copy(
                    nextManualInboundPartNumber = nextPartNumber
                )
            }
        }.onFailure { throwable ->
            Log.e(TAG, "refreshNextManualInboundPartNumberInternal failed", throwable)
        }
    }

    companion object {
        private const val TAG = "InboundViewModel"

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
    val nextManualInboundPartNumber: String = "C01",
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
