package com.example.lcsc_android_erp.data.repository

import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisBindingOp
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisProtocol
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisSlotRecord
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisTableInfo
import com.example.lcsc_android_erp.core.database.AppDatabase
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.model.ContainerSummaryProjection
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerSlot
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.SlotStockItem
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.model.StockOperation
import com.example.lcsc_android_erp.domain.model.StockOperationType
import com.example.lcsc_android_erp.domain.repository.ContainerRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementWrite
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class ContainerRepositoryImpl(
    private val database: AppDatabase,
    private val containerDao: ContainerDao,
    private val componentDao: ComponentDao,
    private val stockPlacementRepository: StockPlacementRepository
) : ContainerRepository {
    override fun observeContainers(): Flow<List<StockContainer>> {
        return containerDao.observeContainerSummaries().map { containers ->
            containers.map(::toStockContainer)
        }
    }

    override fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>> {
        return stockPlacementRepository.observeSlotStock(containerId)
    }

    override fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>> {
        return stockPlacementRepository.observeContainerSlotStock(containerId)
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

    override suspend fun ensureSmartChassisContainer(
        macAddress: String,
        batchId: Int,
        protoVersion: Int,
        batteryPct: Int?,
        statusFlags: Int?,
        tableSeqLow16: Int?,
        advertisedName: String?
    ): StockContainer? {
        val normalizedMac = macAddress.trim().uppercase(Locale.ROOT)
        if (!normalizedMac.matches(MAC_ADDRESS_REGEX) || batchId !in 0..0xFFFF || protoVersion !in 0..0xFF) {
            return null
        }
        val now = System.currentTimeMillis()
        val existing = containerDao.findContainerByMacAddress(normalizedMac)
        val advertisedCode = normalizeSmartChassisName(advertisedName)
        val code = resolveSmartChassisCode(
            advertisedCode = advertisedCode,
            generatedCode = smartChassisCode(normalizedMac, batchId),
            existing = existing
        )
        val displayName = advertisedCode
            ?: existing?.displayName
            ?: "VibeRack ${normalizedMac.takeLast(5).replace(":", "")}"
        val tableSeqLow16Changed = existing?.tableSeq?.let { tableSeq ->
            tableSeqLow16 != null && ((tableSeq and 0xFFFF).toInt() != tableSeqLow16)
        } ?: false
        val containerId = if (existing == null) {
            containerDao.insertContainer(
                ContainerEntity(
                    code = code,
                    displayName = displayName,
                    type = ContainerType.SMART_CHASSIS.name,
                    slotCount = 25,
                    macAddress = normalizedMac,
                    batchId = batchId,
                    protoVersion = protoVersion,
                    batteryPct = batteryPct,
                    statusFlags = statusFlags,
                    lastSeenAt = now,
                    updatedAt = now
                )
            )
        } else {
            containerDao.updateContainer(
                existing.copy(
                    code = code,
                    displayName = displayName,
                    type = ContainerType.SMART_CHASSIS.name,
                    slotCount = 25,
                    macAddress = normalizedMac,
                    batchId = batchId,
                    protoVersion = protoVersion,
                    batteryPct = batteryPct ?: existing.batteryPct,
                    statusFlags = statusFlags ?: existing.statusFlags,
                    tableSeq = existing.tableSeq,
                    lastSyncedAt = if (tableSeqLow16Changed) null else existing.lastSyncedAt,
                    lastSeenAt = now,
                    updatedAt = now
                )
            )
            existing.id
        }
        if (containerId <= 0) {
            return containerDao.findContainerByMacAddress(normalizedMac)?.let(::toStockContainer)
        }
        val slots = (1..25).map { slotNumber ->
            ContainerSlotEntity(
                containerId = containerId,
                slotNumber = slotNumber,
                slotCode = "S%02d".format(slotNumber),
                displayName = "%02d".format(slotNumber),
                sortOrder = slotNumber,
                createdAt = now,
                updatedAt = now
            )
        }
        containerDao.insertSlots(slots)
        return containerDao.findContainerById(containerId)?.let(::toStockContainer)
    }

    override suspend fun getSlots(containerId: Long): List<ContainerSlot> {
        val container = containerDao.findContainerById(containerId) ?: return emptyList()
        return containerDao.getSlots(containerId).map { slot ->
            toContainerSlot(container, slot)
        }
    }

    override suspend fun restoreSmartChassisTable(
        containerId: Long,
        records: List<SmartChassisSlotRecord>,
        tableInfo: SmartChassisTableInfo
    ): Int {
        if (records.size != SmartChassisProtocol.SLOT_COUNT || tableInfo.slotCount != SmartChassisProtocol.SLOT_COUNT) {
            return 0
        }
        return database.withTransaction {
            val container = containerDao.findContainerById(containerId) ?: return@withTransaction 0
            if (container.type != ContainerType.SMART_CHASSIS.name) {
                return@withTransaction 0
            }
            val slots = containerDao.getSlots(containerId).associateBy { it.slotNumber }
            val now = System.currentTimeMillis()
            var restoredCount = 0
            records.forEachIndexed { index, record ->
                val slotNumber = index + 1
                val slot = slots[slotNumber] ?: return@forEachIndexed
                if (record.isEmpty) {
                    stockPlacementRepository.deleteSlotStock(slot.id)
                    return@forEachIndexed
                }
                val protocolPartId = normalizeProtocolPartId(record.partId)
                if (protocolPartId == null) {
                    stockPlacementRepository.deleteSlotStock(slot.id)
                    return@forEachIndexed
                }
                val component = findOrCreateComponentEntity(protocolPartId, now) ?: return@forEachIndexed
                stockPlacementRepository.replaceSlotStock(
                    StockPlacementWrite(
                        componentId = component.id,
                        containerId = containerId,
                        slotId = slot.id,
                        quantity = record.quantity,
                        lastInboundAt = now,
                        updatedAt = now
                    )
                )
                restoredCount++
            }
            containerDao.updateContainer(
                container.copy(
                    tableSeq = tableInfo.tableSeq,
                    tableCrc16 = tableInfo.crc16,
                    lastSyncedAt = now,
                    updatedAt = now
                )
            )
            stockPlacementRepository.recordOperation(
                StockOperation(
                    type = StockOperationType.READ_ALL_RESTORE,
                    containerId = containerId,
                    slotId = null,
                    componentId = null,
                    rawPayload = "records=$restoredCount crc=${tableInfo.crc16}",
                    bleOpcode = SmartChassisBindingOp.READ_ALL.code,
                    tableSeqAfter = tableInfo.tableSeq,
                    createdAt = now
                )
            )
            restoredCount
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

    private suspend fun findOrCreateComponentEntity(
        protocolPartId: String,
        now: Long
    ): ComponentEntity? {
        componentDao.findByProtocolPartId(protocolPartId)?.let { return it }
        componentDao.findByPartNumber(protocolPartId)?.let { existing ->
            if (existing.protocolPartId == protocolPartId) {
                return existing
            }
            val updated = existing.copy(protocolPartId = protocolPartId, updatedAt = now)
            componentDao.update(updated)
            return updated
        }
        val placeholder = ComponentEntity(
            partNumber = protocolPartId,
            protocolPartId = protocolPartId,
            name = protocolPartId,
            updatedAt = now
        )
        val insertedId = componentDao.insert(placeholder)
        return if (insertedId > 0) {
            placeholder.copy(id = insertedId)
        } else {
            componentDao.findByProtocolPartId(protocolPartId)
                ?: componentDao.findByPartNumber(protocolPartId)
        }
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

    private fun normalizeProtocolPartId(value: String): String? {
        return value
            .trim()
            .uppercase(Locale.ROOT)
            .takeIf { it.matches(PROTOCOL_PART_ID_REGEX) }
    }

    private fun smartChassisCode(macAddress: String, batchId: Int): String {
        val suffix = macAddress.replace(":", "").takeLast(4)
        return "VBRK-$suffix-$batchId"
    }

    private suspend fun resolveSmartChassisCode(
        advertisedCode: String?,
        generatedCode: String,
        existing: ContainerEntity?
    ): String {
        if (advertisedCode != null) {
            val owner = containerDao.findContainerByCode(advertisedCode)
            if (owner == null || owner.id == existing?.id) {
                return advertisedCode
            }
        }
        return existing?.code?.takeIf { it.isNotBlank() } ?: generatedCode
    }

    private fun normalizeSmartChassisName(value: String?): String? {
        return value
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(SMART_CHASSIS_NAME_REGEX) }
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
        private val MAC_ADDRESS_REGEX = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
        private val SMART_CHASSIS_NAME_REGEX = Regex("^VBRK-[0-9A-F]{4}$")
    }
}
