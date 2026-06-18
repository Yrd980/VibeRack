package com.viberack.app.core.ble.smart

import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.model.StockContainer
import java.util.Locale

class PhysicalGuidance(
    private val smartChassisOperations: SmartChassisOperations
) {
    val lastOperationError = smartChassisOperations.lastOperationError

    suspend fun findSlot(container: StockContainer, slotNumber: Int): Boolean {
        val macAddress = container.smartChassisMacAddress() ?: return false
        return smartChassisOperations.findSlot(macAddress, slotNumber)
    }

    suspend fun findRecord(record: SearchInventoryRecord): Boolean {
        val target = record.guidanceTarget() ?: return false
        return smartChassisOperations.findSlot(target.macAddress, target.slotNumber)
    }

    suspend fun guideStockIn(container: StockContainer, slot: ContainerSlotStock): Boolean {
        return guideStockIn(container, slot.slot.slotNumber)
    }

    suspend fun guideStockIn(container: StockContainer, slotNumber: Int): Boolean {
        val macAddress = container.smartChassisMacAddress() ?: return false
        return smartChassisOperations.guideStockInSlot(macAddress, slotNumber)
    }

    suspend fun guideStockIn(target: GuidanceTarget): Boolean {
        return smartChassisOperations.guideStockInSlot(target.macAddress, target.slotNumber)
    }

    suspend fun pickGroups(groups: List<PickGroup>): PickGroup? {
        return groups.firstOrNull { group ->
            !smartChassisOperations.pickSlots(group.macAddress, group.slots)
        }
    }

    suspend fun lightsOff(macAddress: String? = null): Boolean {
        return smartChassisOperations.lightsOff(macAddress)
    }

    private fun StockContainer.smartChassisMacAddress(): String? {
        if (type != ContainerType.SMART_CHASSIS) {
            return null
        }
        return macAddress?.normalizeMacAddress()?.takeIf(String::isNotBlank)
    }

    private fun SearchInventoryRecord.guidanceTarget(): GuidanceTarget? {
        val slot = slotNumber ?: return null
        val mac = containerMacAddress?.normalizeMacAddress()?.takeIf(String::isNotBlank) ?: return null
        return if (containerType == ContainerType.SMART_CHASSIS && slot in 1..SmartChassisProtocol.SLOT_COUNT) {
            GuidanceTarget(mac, slot)
        } else {
            null
        }
    }

    private fun String.normalizeMacAddress(): String {
        return trim().uppercase(Locale.ROOT)
    }
}

data class GuidanceTarget(
    val macAddress: String,
    val slotNumber: Int
)

data class PickGroup(
    val macAddress: String,
    val slots: List<Int>
)
