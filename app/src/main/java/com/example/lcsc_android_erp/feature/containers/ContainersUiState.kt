package com.example.lcsc_android_erp.feature.containers

import com.example.lcsc_android_erp.core.ble.smart.SmartChassisConnectionState
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisOperationError
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisTableInfo
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.StockContainer

data class ContainersUiState(
    val containers: List<StockContainer> = emptyList(),
    val selectedContainer: StockContainer? = null,
    val selectedSlots: List<ContainerSlotStock> = emptyList(),
    val connectionState: SmartChassisConnectionState = SmartChassisConnectionState(),
    val activeTableInfo: SmartChassisTableInfo? = null,
    val lastOperationError: SmartChassisOperationError? = null,
    val isScanning: Boolean = false,
    val discoveredCount: Int = 0,
    val scanError: String? = null,
    val activeLightSlot: Int? = null,
    val message: String? = null
)

data class ContainersOpenRequest(
    val containerId: Long? = null,
    val macAddress: String? = null,
    val batchId: Int? = null,
    val protoVersion: Int? = null
)
