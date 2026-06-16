package com.viberack.app.feature.containers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.viberack.app.core.AppContainer
import com.viberack.app.core.ble.smart.SmartChassisConnectionState
import com.viberack.app.core.ble.smart.SmartChassisOperations
import com.viberack.app.core.ble.smart.SmartChassisProtocol
import com.viberack.app.core.ble.smart.SmartChassisScanner
import com.viberack.app.core.ble.smart.SmartChassisOperationError
import com.viberack.app.core.ble.smart.SmartChassisRestorePreview
import com.viberack.app.core.ble.smart.SmartChassisTableInfo
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.StockContainer
import com.viberack.app.domain.repository.ContainerRepository
import com.viberack.app.domain.repository.SlotOperationRepository
import com.viberack.app.domain.repository.SlotOperationWrite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ContainersViewModel(
    private val containerRepository: ContainerRepository,
    private val slotOperationRepository: SlotOperationRepository,
    private val smartChassisOperations: SmartChassisOperations,
    private val smartChassisScanner: SmartChassisScanner
) : ViewModel() {
    private val selectedContainerId = MutableStateFlow<Long?>(null)
    private val activeLightSlot = MutableStateFlow<Int?>(null)
    private val restorePreview = MutableStateFlow<SmartChassisRestorePreview?>(null)
    private val slotInboundRequest = MutableStateFlow<SlotInboundRequest?>(null)
    private val message = MutableStateFlow<String?>(null)
    private var scanRegistrationJob: Job? = null

    private val selectedSlots = selectedContainerId.flatMapLatest { containerId ->
        if (containerId == null) {
            flowOf(emptyList())
        } else {
            containerRepository.observeContainerSlotStock(containerId)
        }
    }

    private val selectedContainerState = combine(
        containerRepository.observeContainers(),
        selectedContainerId,
        selectedSlots
    ) { containers, selectedId, slots ->
        SelectedContainerState(
            containers = containers,
            selectedContainer = selectedId?.let { id ->
                containers.firstOrNull { it.id == id }
            } ?: containers.firstOrNull(),
            selectedSlots = slots
        )
    }

    private val chassisState = combine(
        smartChassisOperations.connectionState,
        smartChassisOperations.activeTableInfo,
        smartChassisOperations.lastOperationError
    ) { connectionState, activeTableInfo, lastOperationError ->
        ChassisState(
            connectionState = connectionState,
            activeTableInfo = activeTableInfo,
            lastOperationError = lastOperationError
        )
    }

    private val dialogState = combine(
        activeLightSlot,
        restorePreview,
        slotInboundRequest,
        message
    ) { activeSlot, preview, inboundRequest, currentMessage ->
        DialogState(
            activeLightSlot = activeSlot,
            restorePreview = preview,
            slotInboundRequest = inboundRequest,
            message = currentMessage
        )
    }

    val uiState: StateFlow<ContainersUiState> = combine(
        selectedContainerState,
        chassisState,
        smartChassisScanner.state,
        dialogState
    ) { selectedState, chassis, scannerState, dialogs ->
        ContainersUiState(
            containers = selectedState.containers,
            selectedContainer = selectedState.selectedContainer,
            selectedSlots = selectedState.selectedSlots,
            connectionState = chassis.connectionState,
            activeTableInfo = chassis.activeTableInfo,
            lastOperationError = chassis.lastOperationError,
            isScanning = scannerState.isScanning,
            discoveredCount = scannerState.devices.size,
            scanError = scannerState.lastError,
            activeLightSlot = dialogs.activeLightSlot,
            restorePreview = dialogs.restorePreview,
            slotInboundRequest = dialogs.slotInboundRequest,
            message = dialogs.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContainersUiState()
    )

    init {
        viewModelScope.launch {
            containerRepository.observeContainers().collect { containers ->
                if (selectedContainerId.value == null) {
                    selectedContainerId.value = containers.firstOrNull()?.id
                } else if (containers.none { it.id == selectedContainerId.value }) {
                    selectedContainerId.value = containers.firstOrNull()?.id
                }
            }
        }
    }

    fun selectContainer(container: StockContainer) {
        selectedContainerId.value = container.id
        activeLightSlot.value = null
        message.value = null
    }

    fun openRequest(request: ContainersOpenRequest) {
        val requestedProtoVersion = request.protoVersion
        if (requestedProtoVersion != null && !isSupportedSmartChassisProtocol(requestedProtoVersion)) {
            message.value = smartChassisProtocolMessage(requestedProtoVersion)
            return
        }
        viewModelScope.launch {
            val container = request.containerId?.let { containerRepository.findContainer(it) }
                ?: request.macAddress?.let { containerRepository.findContainerByMacAddress(it) }
                ?: request.macAddress?.let { macAddress ->
                    containerRepository.ensureSmartChassisContainer(
                        macAddress = macAddress,
                        batchId = request.batchId ?: 0,
                        protoVersion = request.protoVersion ?: 1
                    )
                }
                ?: return@launch
            selectContainer(container)
            if (container.type == ContainerType.SMART_CHASSIS && container.macAddress != null) {
                connectSmartChassis(container)
            }
        }
    }

    fun connectSmartChassis(container: StockContainer) {
        val macAddress = container.macAddress ?: return
        val protoVersion = container.protoVersion
        if (protoVersion != null && !isSupportedSmartChassisProtocol(protoVersion)) {
            message.value = smartChassisProtocolMessage(protoVersion)
            return
        }
        viewModelScope.launch {
            message.value = null
            val connected = smartChassisOperations.connectAndRefresh(macAddress)
            if (connected != null) {
                message.value = "已连接 ${connected.name ?: connected.address}"
            }
        }
    }

    fun readAllSmartChassis(container: StockContainer) {
        viewModelScope.launch {
            message.value = null
            val preview = smartChassisOperations.readRestorePreview(container)
            if (preview != null) {
                restorePreview.value = preview
                message.value = "已读取 ${preview.totalSlots} 个槽位，待确认 ${preview.changedSlots} 个变更"
            }
        }
    }

    fun confirmRestorePreview() {
        val preview = restorePreview.value ?: return
        viewModelScope.launch {
            message.value = null
            val result = smartChassisOperations.confirmRestoreFromPreview(preview)
            if (result != null) {
                restorePreview.value = null
                message.value = "已从硬件恢复 ${result.restoredRecords} 条记录"
            }
        }
    }

    fun cancelRestorePreview() {
        restorePreview.value = null
    }

    fun findSlot(container: StockContainer, slotNumber: Int) {
        if (container.type != ContainerType.SMART_CHASSIS || slotNumber !in 1..25) {
            return
        }
        val macAddress = container.macAddress ?: return
        viewModelScope.launch {
            message.value = null
            if (smartChassisOperations.findSlot(macAddress, slotNumber)) {
                activeLightSlot.value = slotNumber
                message.value = "正在点亮槽位 $slotNumber"
            }
        }
    }

    fun stockInSlot(container: StockContainer, slotNumber: Int) {
        if (container.type != ContainerType.SMART_CHASSIS || slotNumber !in 1..25) {
            return
        }
        val macAddress = container.macAddress ?: return
        viewModelScope.launch {
            message.value = null
            if (smartChassisOperations.guideStockInSlot(macAddress, slotNumber)) {
                activeLightSlot.value = slotNumber
                message.value = "槽位 $slotNumber 已进入入库引导"
            }
        }
    }

    fun requestSlotInbound(container: StockContainer, slot: ContainerSlotStock) {
        slotInboundRequest.value = SlotInboundRequest(
            containerId = container.id,
            containerCode = container.code,
            slotNumber = slot.slot.slotNumber,
            slotCode = slot.slot.slotCode,
            existingPartNumber = slot.stockItem?.partNumber,
            existingQuantity = slot.stockItem?.quantity
        )
        if (container.type == ContainerType.SMART_CHASSIS && slot.slot.slotNumber in 1..25) {
            stockInSlot(container, slot.slot.slotNumber)
        }
    }

    fun confirmSlotInbound(partIdOrNumber: String, quantity: Int) {
        val request = slotInboundRequest.value ?: return
        val normalizedPart = partIdOrNumber.trim().uppercase()
        if (normalizedPart.isBlank() || quantity < 0) {
            message.value = "请输入有效物料编号和数量"
            return
        }
        viewModelScope.launch {
            message.value = null
            val container = slotOperationRepository.findContainer(request.containerId)
            if (container == null) {
                message.value = "容器不存在"
                return@launch
            }
            val component = slotOperationRepository.resolveLocalComponent(normalizedPart)
            if (component == null) {
                message.value = "无法创建物料 $normalizedPart"
                return@launch
            }
            val result = slotOperationRepository.writeOne(
                SlotOperationWrite(
                    containerId = request.containerId,
                    slotNumber = request.slotNumber,
                    component = component,
                    quantity = quantity,
                    sourceType = "SMART_SLOT_INBOUND",
                    rawPayload = "slot=${request.slotNumber};part=$normalizedPart"
                )
            )
            if (result.success) {
                slotInboundRequest.value = null
                message.value = "${request.containerCode}-${request.slotCode} 已入库 ${component.partNumber}"
            } else {
                message.value = result.message ?: "槽位入库失败"
            }
        }
    }

    fun cancelSlotInbound() {
        slotInboundRequest.value = null
    }

    fun clearSlot(container: StockContainer, slot: ContainerSlotStock) {
        viewModelScope.launch {
            val result = slotOperationRepository.clearOne(container.id, slot.slot.slotNumber)
            message.value = if (result.success) {
                "${slot.slot.positionCode} 已清空"
            } else {
                result.message ?: "清空槽位失败"
            }
        }
    }

    fun setSlotQuantity(container: StockContainer, slot: ContainerSlotStock, quantity: Int) {
        viewModelScope.launch {
            val result = slotOperationRepository.setQuantity(container.id, slot.slot.slotNumber, quantity)
            message.value = if (result.success) {
                "${slot.slot.positionCode} 数量已更新"
            } else {
                result.message ?: "数量更新失败"
            }
        }
    }

    fun lightsOff() {
        viewModelScope.launch {
            if (smartChassisOperations.lightsOff()) {
                activeLightSlot.value = null
                message.value = "灯光已关闭"
            }
        }
    }

    fun scanSmartChassis(hasBluetoothScanPermission: Boolean) {
        message.value = null
        scanRegistrationJob?.cancel()
        smartChassisScanner.startScan(hasBluetoothScanPermission)
        scanRegistrationJob = viewModelScope.launch {
            val scannerState = smartChassisScanner.state.first { !it.isScanning }
            var firstRegisteredContainer: StockContainer? = null
            scannerState.devices.distinctBy { it.address }.forEach { device ->
                if (!device.isSupportedProtocol) {
                    return@forEach
                }
                val registeredContainer = containerRepository.ensureSmartChassisContainer(
                    macAddress = device.address,
                    batchId = device.advertisement.batchId,
                    protoVersion = device.advertisement.protoVersion,
                    batteryPct = device.advertisement.batteryPct,
                    statusFlags = device.advertisement.statusFlags,
                    tableSeqLow16 = device.advertisement.tableSeqLow16,
                    advertisedName = device.name
                )
                if (firstRegisteredContainer == null) {
                    firstRegisteredContainer = registeredContainer
                }
            }
            if (scannerState.devices.isNotEmpty()) {
                firstRegisteredContainer?.let(::selectContainer)
                val unsupportedCount = scannerState.devices.count { !it.isSupportedProtocol }
                message.value = if (unsupportedCount > 0) {
                    "发现 ${scannerState.devices.size} 台智能底盘，已跳过 $unsupportedCount 台协议版本不兼容设备"
                } else {
                    "发现 ${scannerState.devices.size} 台智能底盘"
                }
            }
            scanRegistrationJob = null
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ContainersViewModel(
                    containerRepository = appContainer.containerRepository,
                    slotOperationRepository = appContainer.slotOperationRepository,
                    smartChassisOperations = appContainer.smartChassisOperations,
                    smartChassisScanner = appContainer.smartChassisScanner
                )
            }
        }
    }
}

private fun isSupportedSmartChassisProtocol(protoVersion: Int): Boolean {
    return protoVersion == SmartChassisProtocol.PROTOCOL_VERSION
}

private fun smartChassisProtocolMessage(protoVersion: Int): String {
    return if (protoVersion > SmartChassisProtocol.PROTOCOL_VERSION) {
        "智能底盘协议 v$protoVersion 需要升级 APP 后再写入"
    } else {
        "智能底盘协议 v$protoVersion 需要升级固件后再写入"
    }
}

private data class SelectedContainerState(
    val containers: List<StockContainer>,
    val selectedContainer: StockContainer?,
    val selectedSlots: List<ContainerSlotStock>
)

private data class ChassisState(
    val connectionState: SmartChassisConnectionState,
    val activeTableInfo: SmartChassisTableInfo?,
    val lastOperationError: SmartChassisOperationError?
)

private data class DialogState(
    val activeLightSlot: Int?,
    val restorePreview: SmartChassisRestorePreview?,
    val slotInboundRequest: SlotInboundRequest?,
    val message: String?
)
