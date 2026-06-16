package com.viberack.app.feature.containers

import com.viberack.app.core.ble.smart.SmartChassisConnectionState
import com.viberack.app.core.ble.smart.SmartChassisOperationError
import com.viberack.app.core.ble.smart.SmartChassisRestorePreview
import com.viberack.app.core.ble.smart.SmartChassisTableInfo
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.StockContainer

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
    val restorePreview: SmartChassisRestorePreview? = null,
    val slotInboundRequest: SlotInboundRequest? = null,
    val message: String? = null
)

data class ContainersOpenRequest(
    val containerId: Long? = null,
    val macAddress: String? = null,
    val batchId: Int? = null,
    val protoVersion: Int? = null
)

data class SlotInboundRequest(
    val containerId: Long,
    val containerCode: String,
    val slotNumber: Int,
    val slotCode: String,
    val existingPartNumber: String? = null,
    val existingQuantity: Int? = null
)
