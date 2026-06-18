package com.viberack.app.feature.printer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.viberack.app.core.AppContainer
import com.viberack.app.core.ble.printer.BoxLayerLabel
import com.viberack.app.core.ble.printer.BoxLayerLabelRenderer
import com.viberack.app.core.ble.printer.P0BlePrinter
import com.viberack.app.core.ble.printer.P0BlePrinterClient
import com.viberack.app.core.ble.printer.P0BlePrinterState
import com.viberack.app.core.ble.printer.P0BitmapProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrinterUiState(
    val positionCode: String = "",
    val partNumber: String = "",
    val preview: Bitmap? = null,
    val labelError: String? = null,
    val printerState: P0BlePrinterState = P0BlePrinterState()
)

class PrinterViewModel(
    private val printerClient: P0BlePrinterClient
) : ViewModel() {
    private val labelState = MutableStateFlow(PrinterUiState())

    val uiState: StateFlow<PrinterUiState> = combine(
        labelState,
        printerClient.state
    ) { label, printer ->
        label.copy(printerState = printer)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrinterUiState()
    )

    fun setPositionCode(value: String) = updateLabel(positionCode = value)

    fun setPartNumber(value: String) = updateLabel(partNumber = value)

    fun scan() = printerClient.scan()

    fun connect(printer: P0BlePrinter) {
        viewModelScope.launch {
            printerClient.connect(printer)
        }
    }

    fun disconnect() = printerClient.disconnect()

    fun print() {
        val image = renderPrintImage() ?: return
        viewModelScope.launch {
            printerClient.print(P0BitmapProtocol.buildBitmapPrintChunks(image))
        }
    }

    private fun updateLabel(positionCode: String = labelState.value.positionCode, partNumber: String = labelState.value.partNumber) {
        val label = BoxLayerLabel(positionCode, partNumber)
        val preview = if (label.positionCode.isNotEmpty() && label.partNumber.isNotEmpty()) {
            runCatching { BoxLayerLabelRenderer.renderPreview(label) }.getOrNull()
        } else {
            null
        }
        labelState.update {
            it.copy(
                positionCode = positionCode,
                partNumber = partNumber,
                preview = preview,
                labelError = null
            )
        }
    }

    private fun renderPrintImage(): Bitmap? {
        val label = BoxLayerLabel(labelState.value.positionCode, labelState.value.partNumber)
        val error = when {
            label.positionCode.isEmpty() -> "请填写位置。"
            label.partNumber.isEmpty() -> "请填写立创料号。"
            else -> null
        }
        if (error != null) {
            labelState.update { it.copy(labelError = error) }
            return null
        }
        return BoxLayerLabelRenderer.renderP0PrintImage(label)
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PrinterViewModel(appContainer.p0BlePrinterClient)
            }
        }
    }
}
