package com.viberack.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.viberack.app.core.AppContainer
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    inventoryRepository: InventoryRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        inventoryRepository.observeDashboardSummary(),
        inventoryRepository.observeStorageLocations(),
        userPreferencesRepository.preferences
    ) { summary, locations, preferences ->
        HomeUiState(
            summary = summary,
            locations = locations,
            defaultLocationCode = preferences.defaultLocationCode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            inventoryRepository.bootstrapDefaults()
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    inventoryRepository = appContainer.inventoryRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository
                )
            }
        }
    }
}
