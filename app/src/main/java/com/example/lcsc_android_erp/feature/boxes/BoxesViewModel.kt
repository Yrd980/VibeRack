package com.example.lcsc_android_erp.feature.boxes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.AppContainer
import com.example.lcsc_android_erp.domain.model.ComponentBox
import com.example.lcsc_android_erp.domain.model.ComponentBoxLayer
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BoxesViewModel(
    private val boxRepository: BoxRepository,
    private val appContext: Context
) : ViewModel() {
    private val selectedBox = MutableStateFlow<ComponentBox?>(null)
    private val createError = MutableStateFlow<String?>(null)
    private val selectedBoxLayers = selectedBox.flatMapLatest { box ->
        if (box == null) {
            flowOf(emptyList<ComponentBoxLayer>())
        } else {
            boxRepository.observeLayers(box.id)
        }
    }

    val uiState: StateFlow<BoxesUiState> = combine(
        boxRepository.observeBoxes(),
        selectedBox,
        selectedBoxLayers,
        createError
    ) { boxes, selected, layers, error ->
        BoxesUiState(
            boxes = boxes,
            selectedBox = selected?.let { current ->
                boxes.firstOrNull { it.id == current.id } ?: current
            },
            selectedBoxLayers = layers,
            createError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoxesUiState()
    )

    fun selectBox(box: ComponentBox) {
        selectedBox.value = box
    }

    fun createBox(code: String, name: String, layerCount: Int) {
        viewModelScope.launch {
            createError.value = null
            val errorCode = boxRepository.createBox(
                code = code,
                name = name.ifBlank { null },
                layerCount = layerCount
            )
            createError.value = errorCode?.let(::localizedCreateError)
        }
    }

    fun clearCreateError() {
        createError.value = null
    }

    private fun localizedCreateError(errorCode: String): String {
        val resId = when (errorCode) {
            "invalid_code" -> R.string.boxes_create_error_invalid_code
            "duplicate_code" -> R.string.boxes_create_error_duplicate_code
            "invalid_layer_count" -> R.string.boxes_create_error_invalid_layer_count
            else -> R.string.boxes_create_error_unknown
        }
        return appContext.getString(resId)
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BoxesViewModel(
                    boxRepository = appContainer.boxRepository,
                    appContext = appContainer.appContext
                )
            }
        }
    }
}
