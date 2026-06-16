package com.example.lcsc_android_erp.core.ble.smart

import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.repository.ContainerRepository
import java.util.Locale
import kotlinx.coroutines.flow.first
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

    suspend fun readRestorePreview(container: StockContainer): SmartChassisRestorePreview? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!connectIfNeeded(macAddress)) {
            return null
        }
        val snapshot = manager.readAll() ?: return null
        val localSlots = containerRepository.observeContainerSlotStock(container.id).first()
        val localBySlot = localSlots.associateBy { it.slot.slotNumber }
        var occupiedRecords = 0
        var emptyRecords = 0
        var invalidRecords = 0
        var changedSlots = 0
        snapshot.records.forEachIndexed { index, record ->
            val slotNumber = index + 1
            val local = localBySlot[slotNumber]?.stockItem
            if (record.isEmpty) {
                emptyRecords++
                if (local != null) {
                    changedSlots++
                }
                return@forEachIndexed
            }
            val protocolPartId = record.partId.trim().uppercase(Locale.ROOT)
            if (!protocolPartId.matches(PROTOCOL_PART_ID_REGEX)) {
                invalidRecords++
                if (local != null) {
                    changedSlots++
                }
                return@forEachIndexed
            }
            occupiedRecords++
            if (local?.protocolPartId?.trim()?.uppercase(Locale.ROOT) != protocolPartId ||
                local.quantity != record.quantity
            ) {
                changedSlots++
            }
        }
        return SmartChassisRestorePreview(
            containerId = container.id,
            totalSlots = snapshot.records.size,
            occupiedRecords = occupiedRecords,
            emptyRecords = emptyRecords,
            invalidRecords = invalidRecords,
            changedSlots = changedSlots,
            tableInfo = snapshot.tableInfo,
            records = snapshot.records
        )
    }

    suspend fun confirmRestoreFromPreview(preview: SmartChassisRestorePreview): SmartChassisRestoreResult? {
        val restoredCount = containerRepository.restoreSmartChassisTable(
            containerId = preview.containerId,
            records = preview.records,
            tableInfo = preview.tableInfo
        )
        return SmartChassisRestoreResult(
            totalSlots = preview.totalSlots,
            restoredRecords = restoredCount,
            tableInfo = preview.tableInfo
        )
    }

    suspend fun writeSlot(
        container: StockContainer,
        slotNumber: Int,
        protocolPartId: String,
        quantity: Int
    ): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (slotNumber !in 1..SmartChassisProtocol.SLOT_COUNT || quantity !in 0..0xFFFF) {
            return null
        }
        val normalizedPartId = protocolPartId.trim().uppercase(Locale.ROOT)
        if (!normalizedPartId.matches(PROTOCOL_PART_ID_REGEX) || !connectIfNeeded(macAddress)) {
            return null
        }
        return manager.writeOne(
            SmartChassisSlotRecord(
                slot = slotNumber,
                partId = normalizedPartId,
                quantity = quantity,
                flags = if (normalizedPartId.startsWith("M")) {
                    SmartChassisProtocol.SLOT_FLAG_CUSTOM_PART
                } else {
                    0
                },
                crc8 = 0
            )
        )
    }

    suspend fun clearSlot(container: StockContainer, slotNumber: Int): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!isValidSlot(slotNumber) || !connectIfNeeded(macAddress)) {
            return null
        }
        return manager.clearOne(slotNumber)
    }

    suspend fun setSlotQuantity(container: StockContainer, slotNumber: Int, quantity: Int): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!isValidSlot(slotNumber) || quantity !in 0..0xFFFF || !connectIfNeeded(macAddress)) {
            return null
        }
        return manager.setQuantity(slotNumber, quantity)
    }

    suspend fun insertSlot(
        container: StockContainer,
        slotNumber: Int,
        protocolPartId: String,
        quantity: Int
    ): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        val normalizedPartId = protocolPartId.trim().uppercase(Locale.ROOT)
        if (!isValidSlot(slotNumber) ||
            quantity !in 0..0xFFFF ||
            !normalizedPartId.matches(PROTOCOL_PART_ID_REGEX) ||
            !connectIfNeeded(macAddress)
        ) {
            return null
        }
        return manager.insertAt(
            slotNumber,
            SmartChassisSlotRecord(
                slot = slotNumber,
                partId = normalizedPartId,
                quantity = quantity,
                flags = if (normalizedPartId.startsWith("M")) {
                    SmartChassisProtocol.SLOT_FLAG_CUSTOM_PART
                } else {
                    0
                },
                crc8 = 0
            )
        )
    }

    suspend fun removeSlot(container: StockContainer, slotNumber: Int): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!isValidSlot(slotNumber) || !connectIfNeeded(macAddress)) {
            return null
        }
        return manager.removeAt(slotNumber)
    }

    suspend fun moveBlock(
        container: StockContainer,
        fromSlotNumber: Int,
        toSlotNumber: Int,
        length: Int
    ): SmartChassisTableInfo? {
        val macAddress = container.validSmartChassisMacAddress() ?: return null
        if (!isValidBlock(fromSlotNumber, toSlotNumber, length) || !connectIfNeeded(macAddress)) {
            return null
        }
        return manager.moveBlock(fromSlotNumber, toSlotNumber, length)
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
        if (protoVersion != null && protoVersion != SmartChassisProtocol.PROTOCOL_VERSION) {
            return null
        }
        return macAddress?.normalizeMacAddress()?.takeIf(String::isNotBlank)
    }

    private fun isValidSlot(slotNumber: Int): Boolean {
        return slotNumber in 1..SmartChassisProtocol.SLOT_COUNT
    }

    private fun isValidBlock(from: Int, to: Int, length: Int): Boolean {
        return isValidSlot(from) &&
            isValidSlot(to) &&
            length in 1..SmartChassisProtocol.SLOT_COUNT &&
            from + length - 1 <= SmartChassisProtocol.SLOT_COUNT &&
            to + length - 1 <= SmartChassisProtocol.SLOT_COUNT
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

data class SmartChassisRestorePreview(
    val containerId: Long,
    val totalSlots: Int,
    val occupiedRecords: Int,
    val emptyRecords: Int,
    val invalidRecords: Int,
    val changedSlots: Int,
    val tableInfo: SmartChassisTableInfo,
    val records: List<SmartChassisSlotRecord>
)

private val PROTOCOL_PART_ID_REGEX = Regex("^[CM][A-Z0-9]{0,9}$")
