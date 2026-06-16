package com.viberack.app.feature.boxes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.viberack.app.R
import com.viberack.app.core.AppContainer
import com.viberack.app.core.network.isNetworkAvailable
import com.viberack.app.domain.model.ComponentBox
import com.viberack.app.domain.model.ComponentBoxLayer
import com.viberack.app.domain.repository.BoxRepository
import com.viberack.app.domain.repository.LcscCatalogRepository
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BoxesViewModel(
    private val boxRepository: BoxRepository,
    private val lcscCatalogRepository: LcscCatalogRepository,
    private val appContext: Context
) : ViewModel() {
    private val selectedBox = MutableStateFlow<ComponentBox?>(null)
    private val highlightedLayerId = MutableStateFlow<Long?>(null)
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
        highlightedLayerId,
        createError
    ) { boxes, selected, layers, highlighted, error ->
        BoxesUiState(
            boxes = boxes,
            selectedBox = selected?.let { current ->
                boxes.firstOrNull { it.id == current.id } ?: current
            },
            selectedBoxLayers = layers,
            highlightedLayerId = highlighted,
            createError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoxesUiState()
    )

    fun selectBox(box: ComponentBox) {
        selectedBox.value = box
        highlightedLayerId.value = null
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

    fun openLayer(boxCode: String, layerCode: String) {
        viewModelScope.launch {
            val targetLayer = boxRepository.findLayerByPosition(boxCode, layerCode)
                ?: return@launch
            val targetBox = boxRepository.observeBoxes()
                .first()
                .firstOrNull { it.id == targetLayer.boxId }
                ?: return@launch
            selectedBox.value = targetBox
            highlightedLayerId.value = targetLayer.id
        }
    }

    fun bindLayerComponent(
        layer: ComponentBoxLayer,
        partNumber: String,
        quantity: Int,
        onCompleted: (String?) -> Unit
    ) {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        if (normalizedPartNumber.isBlank() || quantity < 0) {
            onCompleted(appContext.getString(R.string.boxes_bind_error_invalid_input))
            return
        }
        if (!appContext.isNetworkAvailable()) {
            onCompleted(appContext.getString(R.string.common_network_unavailable))
            return
        }

        viewModelScope.launch {
            val component = lcscCatalogRepository.lookupByPartNumber(normalizedPartNumber)
            if (component == null) {
                onCompleted(
                    appContext.getString(
                        R.string.boxes_bind_error_not_found,
                        normalizedPartNumber
                    )
                )
                return@launch
            }
            val errorCode = boxRepository.bindComponentToLayer(
                layerId = layer.id,
                component = component,
                quantity = quantity,
                sourceType = "BOX_LAYER_MANUAL"
            )
            onCompleted(errorCode?.let(::localizedBindError))
            if (errorCode == null) {
                highlightedLayerId.value = layer.id
            }
        }
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

    private fun localizedBindError(errorCode: String): String {
        val resId = when (errorCode) {
            "invalid_quantity" -> R.string.boxes_bind_error_invalid_input
            "layer_not_found" -> R.string.boxes_bind_error_layer_not_found
            else -> R.string.boxes_bind_error_unknown
        }
        return appContext.getString(resId)
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BoxesViewModel(
                    boxRepository = appContainer.boxRepository,
                    lcscCatalogRepository = appContainer.lcscCatalogRepository,
                    appContext = appContainer.appContext
                )
            }
        }
    }
}
