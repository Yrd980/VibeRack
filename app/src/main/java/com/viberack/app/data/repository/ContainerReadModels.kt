package com.viberack.app.data.repository

import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.model.ContainerSlotStockProjection
import com.viberack.app.core.database.model.ContainerSummaryProjection
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ContainerSlot
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.SlotStockItem
import com.viberack.app.domain.model.StockContainer
import org.json.JSONObject

internal object ContainerReadModels {
    fun stockContainer(projection: ContainerSummaryProjection): StockContainer {
        return StockContainer(
            id = projection.id,
            code = projection.code,
            displayName = projection.displayName,
            type = projection.type.toContainerType(),
            slotCount = projection.slotCount,
            colorHex = projection.colorHex,
            sortMode = projection.sortMode,
            remark = projection.remark,
            createdAt = projection.createdAt,
            updatedAt = projection.updatedAt,
            macAddress = projection.macAddress,
            batchId = projection.batchId,
            protoVersion = projection.protoVersion,
            firmwareVersion = projection.firmwareVersion,
            hardwareVersion = projection.hardwareVersion,
            batteryPct = projection.batteryPct,
            statusFlags = projection.statusFlags,
            tableSeq = projection.tableSeq,
            tableCrc16 = projection.tableCrc16,
            lastSeenAt = projection.lastSeenAt,
            lastSyncedAt = projection.lastSyncedAt
        )
    }

    fun stockContainer(entity: ContainerEntity): StockContainer {
        return StockContainer(
            id = entity.id,
            code = entity.code,
            displayName = entity.displayName,
            type = entity.type.toContainerType(),
            slotCount = entity.slotCount,
            colorHex = entity.colorHex,
            sortMode = entity.sortMode,
            remark = entity.remark,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            macAddress = entity.macAddress,
            batchId = entity.batchId,
            protoVersion = entity.protoVersion,
            firmwareVersion = entity.firmwareVersion,
            hardwareVersion = entity.hardwareVersion,
            batteryPct = entity.batteryPct,
            statusFlags = entity.statusFlags,
            tableSeq = entity.tableSeq,
            tableCrc16 = entity.tableCrc16,
            lastSeenAt = entity.lastSeenAt,
            lastSyncedAt = entity.lastSyncedAt
        )
    }

    fun containerSlot(container: ContainerEntity, slot: ContainerSlotEntity): ContainerSlot {
        return ContainerSlot(
            id = slot.id,
            containerId = slot.containerId,
            containerCode = container.code,
            containerType = container.type.toContainerType(),
            slotNumber = slot.slotNumber,
            slotCode = slot.slotCode,
            displayName = slot.displayName,
            sortOrder = slot.sortOrder
        )
    }

    fun containerSlotStock(projection: ContainerSlotStockProjection): ContainerSlotStock {
        val slot = ContainerSlot(
            id = projection.slotId,
            containerId = projection.containerId,
            containerCode = projection.containerCode,
            containerType = projection.containerType.toContainerType(),
            slotNumber = projection.slotNumber,
            slotCode = projection.slotCode,
            displayName = projection.slotDisplayName,
            sortOrder = projection.sortOrder
        )
        return ContainerSlotStock(
            slot = slot,
            stockItem = slotStockItem(projection)
        )
    }

    fun slotStockItem(projection: ContainerSlotStockProjection): SlotStockItem? {
        val stockItemId = projection.stockItemId ?: return null
        val componentId = projection.componentId ?: return null
        val partNumber = projection.partNumber ?: return null
        val quantity = projection.quantity ?: return null
        return SlotStockItem(
            id = stockItemId,
            componentId = componentId,
            containerId = projection.containerId,
            slotId = projection.slotId,
            slotNumber = projection.slotNumber,
            partNumber = partNumber,
            protocolPartId = projection.protocolPartId ?: partNumber,
            quantity = quantity,
            quantityState = projection.quantityState.toQuantityState(),
            safetyStockThreshold = projection.safetyStockThreshold,
            mpn = projection.mpn,
            name = projection.name,
            brand = projection.brand,
            packageName = projection.packageName,
            category = projection.category,
            description = projection.description,
            sourceUrl = projection.sourceUrl,
            specifications = parseSpecifications(projection.specJson),
            imageLocalPath = projection.imageLocalPath,
            updatedAt = projection.updatedAt ?: 0
        )
    }

    fun componentDetail(entity: ComponentEntity): ComponentDetail {
        return ComponentDetail(
            partNumber = entity.partNumber,
            mpn = entity.mpn,
            name = entity.name,
            brand = entity.brand,
            packageName = entity.packageName,
            category = entity.category,
            description = entity.description,
            stockQuantity = null,
            price = null,
            productUrl = entity.sourceUrl,
            datasheetUrl = null,
            imageLocalPath = entity.imageLocalPath,
            imageUrl = null,
            specifications = parseSpecifications(entity.specJson)
        )
    }

    fun parseSpecifications(specJson: String?): Map<String, String> {
        if (specJson.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            val json = JSONObject(specJson)
            json.keys().asSequence().associateWith { key ->
                json.optString(key)
            }.filterValues { value ->
                value.isNotBlank() && value != "null"
            }
        }.getOrDefault(emptyMap())
    }

    fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }

    private fun String?.toQuantityState(): QuantityState {
        return this
            ?.let { value -> runCatching { QuantityState.valueOf(value) }.getOrNull() }
            ?: QuantityState.KNOWN
    }
}
