package com.viberack.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.viberack.app.core.AppContainer
import com.viberack.app.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    inventoryRepository: InventoryRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = inventoryRepository.observeDashboardSummary().map { summary ->
        HomeUiState(
            summary = summary
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    inventoryRepository = appContainer.inventoryRepository
                )
            }
        }
    }
}
