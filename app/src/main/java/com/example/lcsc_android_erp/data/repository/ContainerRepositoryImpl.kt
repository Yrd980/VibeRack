package com.example.lcsc_android_erp.data.repository

import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.model.ContainerSlotStockProjection
import com.example.lcsc_android_erp.core.database.model.ContainerSummaryProjection
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerSlot
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.QuantityState
import com.example.lcsc_android_erp.domain.model.SlotStockItem
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.repository.ContainerRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class ContainerRepositoryImpl(
    private val containerDao: ContainerDao,
    private val componentDao: ComponentDao
) : ContainerRepository {
    override fun observeContainers(): Flow<List<StockContainer>> {
        return containerDao.observeContainerSummaries().map { containers ->
            containers.map(::toStockContainer)
        }
    }

    override fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>> {
        return containerDao.observeSlotStock(containerId).map { rows ->
            rows.mapNotNull(::toSlotStockItem)
        }
    }

    override fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>> {
        return containerDao.observeSlotStock(containerId).map { rows ->
            rows.map(::toContainerSlotStock)
        }
    }

    override suspend fun findContainer(containerId: Long): StockContainer? {
        return containerDao.findContainerById(containerId)?.let(::toStockContainer)
    }

    override suspend fun findContainerByMacAddress(macAddress: String): StockContainer? {
        val normalizedMac = macAddress.trim().uppercase(Locale.ROOT)
        if (normalizedMac.isBlank()) {
            return null
        }
        return containerDao.findContainerByMacAddress(normalizedMac)?.let(::toStockContainer)
    }

    override suspend fun getSlots(containerId: Long): List<ContainerSlot> {
        val container = containerDao.findContainerById(containerId) ?: return emptyList()
        return containerDao.getSlots(containerId).map { slot ->
            toContainerSlot(container, slot)
        }
    }

    override suspend fun findComponentByProtocolPartId(protocolPartId: String): ComponentDetail? {
        val normalizedPartId = normalizeProtocolPartId(protocolPartId) ?: return null
        val component = componentDao.findByProtocolPartId(normalizedPartId)
            ?: componentDao.findByPartNumber(normalizedPartId)
        return component?.let(::toComponentDetail)
    }

    override suspend fun findOrCreateManualPlaceholder(protocolPartId: String): ComponentDetail {
        val normalizedPartId = normalizeProtocolPartId(protocolPartId)
            ?: error("Invalid protocol part id: $protocolPartId")
        componentDao.findByProtocolPartId(normalizedPartId)?.let { return toComponentDetail(it) }

        val now = System.currentTimeMillis()
        val component = ComponentEntity(
            partNumber = normalizedPartId,
            protocolPartId = normalizedPartId,
            name = normalizedPartId,
            updatedAt = now
        )
        val insertId = componentDao.insert(component)
        val resolved = if (insertId > 0) {
            component.copy(id = insertId)
        } else {
            componentDao.findByProtocolPartId(normalizedPartId)
                ?: componentDao.findByPartNumber(normalizedPartId)?.let { existing ->
                    if (existing.protocolPartId == null) {
                        val updated = existing.copy(protocolPartId = normalizedPartId)
                        componentDao.update(updated)
                        updated
                    } else {
                        existing
                    }
                }
                ?: error("Failed to resolve placeholder component for $normalizedPartId")
        }
        return toComponentDetail(resolved)
    }

    private fun toStockContainer(projection: ContainerSummaryProjection): StockContainer {
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

    private fun toStockContainer(entity: ContainerEntity): StockContainer {
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

    private fun toContainerSlot(container: ContainerEntity, slot: ContainerSlotEntity): ContainerSlot {
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

    private fun toSlotStockItem(projection: ContainerSlotStockProjection): SlotStockItem? {
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

    private fun toContainerSlotStock(projection: ContainerSlotStockProjection): ContainerSlotStock {
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
            stockItem = toSlotStockItem(projection)
        )
    }

    private fun toComponentDetail(entity: ComponentEntity): ComponentDetail {
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

    private fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }

    private fun String?.toQuantityState(): QuantityState {
        return this
            ?.let { value -> runCatching { QuantityState.valueOf(value) }.getOrNull() }
            ?: QuantityState.KNOWN
    }

    private fun normalizeProtocolPartId(value: String): String? {
        return value
            .trim()
            .uppercase(Locale.ROOT)
            .takeIf { it.matches(PROTOCOL_PART_ID_REGEX) }
    }

    private fun parseSpecifications(specJson: String?): Map<String, String> {
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

    private companion object {
        private val PROTOCOL_PART_ID_REGEX = Regex("^[CM][A-Z0-9]{0,9}$")
    }
}
