package com.example.lcsc_android_erp.feature.containers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lcsc_android_erp.core.AppContainer
import com.example.lcsc_android_erp.core.ble.smart.RgbColor
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisConnectionState
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisCodec
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisLightCommand
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisLightMode
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisManager
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisScanner
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisOperationError
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisTableInfo
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.repository.ContainerRepository
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
    private val smartChassisManager: SmartChassisManager,
    private val smartChassisScanner: SmartChassisScanner
) : ViewModel() {
    private val selectedContainerId = MutableStateFlow<Long?>(null)
    private val activeLightSlot = MutableStateFlow<Int?>(null)
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
        smartChassisManager.connectionState,
        smartChassisManager.activeTableInfo,
        smartChassisManager.lastOperationError
    ) { connectionState, activeTableInfo, lastOperationError ->
        ChassisState(
            connectionState = connectionState,
            activeTableInfo = activeTableInfo,
            lastOperationError = lastOperationError
        )
    }

    val uiState: StateFlow<ContainersUiState> = combine(
        selectedContainerState,
        chassisState,
        smartChassisScanner.state,
        activeLightSlot,
        message
    ) { selectedState, chassis, scannerState, activeSlot, currentMessage ->
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
            activeLightSlot = activeSlot,
            message = currentMessage
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
        viewModelScope.launch {
            message.value = null
            val connected = smartChassisManager.connect(macAddress)
            if (connected != null) {
                smartChassisManager.refreshTableInfo()
                message.value = "已连接 ${connected.name ?: connected.address}"
            }
        }
    }

    fun readAllSmartChassis(container: StockContainer) {
        val macAddress = container.macAddress ?: return
        viewModelScope.launch {
            message.value = null
            if (!smartChassisManager.connectionState.value.isConnected) {
                smartChassisManager.connect(macAddress)
            }
            val snapshot = smartChassisManager.readAll()
            if (snapshot != null) {
                val restoredCount = containerRepository.restoreSmartChassisTable(
                    containerId = container.id,
                    records = snapshot.records,
                    tableInfo = snapshot.tableInfo
                )
                message.value = "已读取 ${snapshot.records.size} 个槽位，恢复 $restoredCount 条记录"
            }
        }
    }

    fun findSlot(container: StockContainer, slotNumber: Int) {
        if (container.type != ContainerType.SMART_CHASSIS || slotNumber !in 1..25) {
            return
        }
        val macAddress = container.macAddress ?: return
        viewModelScope.launch {
            message.value = null
            if (!smartChassisManager.connectionState.value.isConnected) {
                smartChassisManager.connect(macAddress)
            }
            val status = smartChassisManager.sendLightCommand(
                SmartChassisLightCommand(
                    mode = SmartChassisLightMode.FIND,
                    maskA = SmartChassisCodec.slotMask(slotNumber),
                    colorA = RgbColor(red = 40, green = 180, blue = 255),
                    timeoutSeconds = 30
                )
            )
            if (status != null) {
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
            if (!smartChassisManager.connectionState.value.isConnected) {
                smartChassisManager.connect(macAddress)
            }
            val status = smartChassisManager.sendLightCommand(
                SmartChassisLightCommand(
                    mode = SmartChassisLightMode.STOCK_IN,
                    maskA = SmartChassisCodec.slotMask(slotNumber),
                    colorA = RgbColor(red = 30, green = 220, blue = 100),
                    timeoutSeconds = 45
                )
            )
            if (status != null) {
                activeLightSlot.value = slotNumber
                message.value = "槽位 $slotNumber 已进入入库引导"
            }
        }
    }

    fun lightsOff() {
        viewModelScope.launch {
            val status = smartChassisManager.sendLightCommand(
                SmartChassisLightCommand(
                    mode = SmartChassisLightMode.OFF,
                    maskA = 0,
                    colorA = RgbColor(red = 0, green = 0, blue = 0)
                )
            )
            if (status != null) {
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
                val registeredContainer = containerRepository.ensureSmartChassisContainer(
                    macAddress = device.address,
                    batchId = device.advertisement.batchId,
                    protoVersion = device.advertisement.protoVersion,
                    batteryPct = device.advertisement.batteryPct,
                    statusFlags = device.advertisement.statusFlags,
                    tableSeqLow16 = device.advertisement.tableSeqLow16
                )
                if (firstRegisteredContainer == null) {
                    firstRegisteredContainer = registeredContainer
                }
            }
            if (scannerState.devices.isNotEmpty()) {
                firstRegisteredContainer?.let(::selectContainer)
                message.value = "发现 ${scannerState.devices.size} 台智能底盘"
            }
            scanRegistrationJob = null
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ContainersViewModel(
                    containerRepository = appContainer.containerRepository,
                    smartChassisManager = appContainer.smartChassisManager,
                    smartChassisScanner = appContainer.smartChassisScanner
                )
            }
        }
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
