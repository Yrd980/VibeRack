package com.example.lcsc_android_erp.core.ble.smart

import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.repository.ContainerRepository
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

class SmartChassisOperations(
    private val manager: SmartChassisManager,
    private val containerRepository: ContainerRepository
) {
    val connectionState: StateFlow<SmartChassisConnectionState> = manager.connectionState
    val activeTableInfo: StateFlow<SmartChassisTableInfo?> = manager.activeTableInfo
    val lastOperationError: StateFlow<SmartChassisOperationError?> = manager.lastOperationError

    suspend fun connectAndRefresh(macAddress: String): SmartChassisDevice? {
        val connected = manager.connect(macAddress.normalizeMacAddress())
        if (connected != null) {
            manager.refreshTableInfo()
        }
        return connected
    }

    suspend fun restoreFromHardware(container: StockContainer): SmartChassisRestoreResult? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!connectIfNeeded(macAddress)) {
            return null
        }
        val snapshot = manager.readAll() ?: return null
        val restoredCount = containerRepository.restoreSmartChassisTable(
            containerId = container.id,
            records = snapshot.records,
            tableInfo = snapshot.tableInfo
        )
        return SmartChassisRestoreResult(
            totalSlots = snapshot.records.size,
            restoredRecords = restoredCount,
            tableInfo = snapshot.tableInfo
        )
    }

    suspend fun findSlot(macAddress: String, slotNumber: Int): Boolean {
        return lightSingleSlot(
            macAddress = macAddress,
            slotNumber = slotNumber,
            mode = SmartChassisLightMode.FIND,
            color = RgbColor(red = 40, green = 180, blue = 255),
            timeoutSeconds = 30
        )
    }

    suspend fun guideStockInSlot(macAddress: String, slotNumber: Int): Boolean {
        return lightSingleSlot(
            macAddress = macAddress,
            slotNumber = slotNumber,
            mode = SmartChassisLightMode.STOCK_IN,
            color = RgbColor(red = 30, green = 220, blue = 100),
            timeoutSeconds = 45
        )
    }

    suspend fun pickSlots(macAddress: String, slotNumbers: List<Int>): Boolean {
        val normalizedMac = macAddress.normalizeMacAddress()
        val mask = slotNumbers
            .distinct()
            .filter(::isValidSlot)
            .fold(0) { currentMask, slotNumber ->
                currentMask or SmartChassisCodec.slotMask(slotNumber)
            }
        if (mask == 0 || !connectIfNeeded(normalizedMac)) {
            return false
        }
        return manager.sendLightCommand(
            SmartChassisLightCommand(
                mode = SmartChassisLightMode.PICK,
                maskA = mask,
                colorA = RgbColor(red = 255, green = 180, blue = 40),
                timeoutSeconds = 300
            )
        ) != null
    }

    suspend fun lightsOff(macAddress: String? = null): Boolean {
        val normalizedMac = macAddress?.normalizeMacAddress()
        if (normalizedMac != null && !connectIfNeeded(normalizedMac)) {
            return false
        }
        return manager.sendLightCommand(
            SmartChassisLightCommand(
                mode = SmartChassisLightMode.OFF,
                maskA = 0,
                colorA = RgbColor(red = 0, green = 0, blue = 0)
            )
        ) != null
    }

    private suspend fun lightSingleSlot(
        macAddress: String,
        slotNumber: Int,
        mode: SmartChassisLightMode,
        color: RgbColor,
        timeoutSeconds: Int
    ): Boolean {
        val normalizedMac = macAddress.normalizeMacAddress()
        if (!isValidSlot(slotNumber) || !connectIfNeeded(normalizedMac)) {
            return false
        }
        return manager.sendLightCommand(
            SmartChassisLightCommand(
                mode = mode,
                maskA = SmartChassisCodec.slotMask(slotNumber),
                colorA = color,
                timeoutSeconds = timeoutSeconds
            )
        ) != null
    }

    private suspend fun connectIfNeeded(macAddress: String): Boolean {
        val normalizedMac = macAddress.normalizeMacAddress()
        val connection = manager.connectionState.value
        if (connection.isConnected &&
            connection.device?.address?.normalizeMacAddress() == normalizedMac
        ) {
            return true
        }
        return manager.connect(normalizedMac) != null
    }

    private fun StockContainer.validSmartChassisMacAddress(): String? {
        if (type != ContainerType.SMART_CHASSIS) {
            return null
        }
        return macAddress?.normalizeMacAddress()?.takeIf(String::isNotBlank)
    }

    private fun isValidSlot(slotNumber: Int): Boolean {
        return slotNumber in 1..SmartChassisProtocol.SLOT_COUNT
    }

    private fun String.normalizeMacAddress(): String {
        return trim().uppercase(Locale.ROOT)
    }
}

data class SmartChassisRestoreResult(
    val totalSlots: Int,
    val restoredRecords: Int,
    val tableInfo: SmartChassisTableInfo
)
