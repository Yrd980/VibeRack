package com.example.lcsc_android_erp.data.repository

import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisBindingOp
import com.example.lcsc_android_erp.core.database.AppDatabase
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockContainer
import com.example.lcsc_android_erp.domain.model.StockOperation
import com.example.lcsc_android_erp.domain.model.StockOperationType
import com.example.lcsc_android_erp.domain.repository.SlotOperationRepository
import com.example.lcsc_android_erp.domain.repository.SlotOperationResult
import com.example.lcsc_android_erp.domain.repository.SlotOperationWrite
import com.example.lcsc_android_erp.domain.repository.StockPlacementRepository
import com.example.lcsc_android_erp.domain.repository.StockPlacementWrite
import java.util.Locale
import org.json.JSONObject

class SlotOperationRepositoryImpl(
    private val database: AppDatabase,
    private val containerDao: ContainerDao,
    private val componentDao: ComponentDao,
    private val stockPlacementRepository: StockPlacementRepository
) : SlotOperationRepository {
    override suspend fun writeOne(write: SlotOperationWrite): SlotOperationResult {
        if (write.quantity < 0) {
            return SlotOperationResult(success = false, message = ERROR_INVALID_QUANTITY)
        }
        return database.withTransaction {
            val context = resolveContext(write.containerId, write.slotNumber)
                ?: return@withTransaction SlotOperationResult(false, ERROR_SLOT_NOT_FOUND)
            if (!context.supportsSingleSlotWrite()) {
                return@withTransaction SlotOperationResult(false, ERROR_UNSUPPORTED_OPERATION)
            }
            val component = upsertComponent(write.component, write.sourceType)
            replaceSlotStock(
                context = context,
                componentId = component.id,
                quantity = write.quantity,
                operationType = StockOperationType.WRITE_ONE,
                sourceType = write.sourceType,
                sourceRef = component.partNumber,
                rawPayload = write.rawPayload,
                bleOpcode = if (context.container.type == ContainerType.SMART_CHASSIS) {
                    SmartChassisBindingOp.WRITE_ONE.code
                } else {
                    null
                }
            )
            SlotOperationResult(
                success = true,
                affectedSlots = listOfNotNull(stockPlacementRepository.findSlotStock(context.slot.slot.id))
            )
        }
    }

    override suspend fun clearOne(containerId: Long, slotNumber: Int): SlotOperationResult {
        return database.withTransaction {
            val context = resolveContext(containerId, slotNumber)
                ?: return@withTransaction SlotOperationResult(false, ERROR_SLOT_NOT_FOUND)
            if (!context.supportsSingleSlotWrite()) {
                return@withTransaction SlotOperationResult(false, ERROR_UNSUPPORTED_OPERATION)
            }
            val existing = stockPlacementRepository.findSlotStock(context.slot.slot.id)?.stockItem
            stockPlacementRepository.deleteSlotStock(context.slot.slot.id)
            recordOperation(
                type = StockOperationType.CLEAR_ONE,
                context = context,
                componentId = existing?.componentId,
                quantityDelta = existing?.quantity?.let { -it } ?: 0,
                bleOpcode = if (context.container.type == ContainerType.SMART_CHASSIS) {
                    SmartChassisBindingOp.CLEAR_ONE.code
                } else {
                    null
                }
            )
            SlotOperationResult(
                success = true,
                affectedSlots = listOfNotNull(stockPlacementRepository.findSlotStock(context.slot.slot.id))
            )
        }
    }

    override suspend fun insertAt(write: SlotOperationWrite): SlotOperationResult {
        if (write.quantity < 0) {
            return SlotOperationResult(false, ERROR_INVALID_QUANTITY)
        }
        return database.withTransaction {
            val container = containerDao.findContainerById(write.containerId)?.toStockContainer()
                ?: return@withTransaction SlotOperationResult(false, ERROR_CONTAINER_NOT_FOUND)
            if (container.type == ContainerType.LEGACY_LOCATION) {
                return@withTransaction SlotOperationResult(false, ERROR_UNSUPPORTED_OPERATION)
            }
            val slots = stockPlacementRepository.getContainerSlotStock(write.containerId)
            if (write.slotNumber !in 1..container.slotCount || slots.size < container.slotCount) {
                return@withTransaction SlotOperationResult(false, ERROR_SLOT_NOT_FOUND)
            }
            if (slots.lastOrNull()?.stockItem != null) {
                return@withTransaction SlotOperationResult(false, ERROR_CONTAINER_FULL)
            }
            val component = upsertComponent(write.component, write.sourceType)
            val shifted = shiftRight(
                slots = slots,
                fromSlotNumber = write.slotNumber,
                now = System.currentTimeMillis()
            )
            val target = slots.first { it.slot.slotNumber == write.slotNumber }
            replaceSlotStock(
                context = OperationContext(container, target),
                componentId = component.id,
                quantity = write.quantity,
                operationType = StockOperationType.INSERT_AT,
                sourceType = write.sourceType,
                sourceRef = component.partNumber,
                rawPayload = write.rawPayload,
                bleOpcode = bleOpcode(container, SmartChassisBindingOp.INSERT_AT)
            )
            SlotOperationResult(true, affectedSlots = shifted + listOfNotNull(stockPlacementRepository.findSlotStock(target.slot.id)))
        }
    }

    override suspend fun removeAt(containerId: Long, slotNumber: Int): SlotOperationResult {
        return database.withTransaction {
            val container = containerDao.findContainerById(containerId)?.toStockContainer()
                ?: return@withTransaction SlotOperationResult(false, ERROR_CONTAINER_NOT_FOUND)
            if (container.type == ContainerType.LEGACY_LOCATION) {
                return@withTransaction SlotOperationResult(false, ERROR_UNSUPPORTED_OPERATION)
            }
            val slots = stockPlacementRepository.getContainerSlotStock(containerId)
            if (slotNumber !in 1..container.slotCount || slots.size < container.slotCount) {
                return@withTransaction SlotOperationResult(false, ERROR_SLOT_NOT_FOUND)
            }
            val removed = slots.first { it.slot.slotNumber == slotNumber }
            val removedStock = removed.stockItem
            val changed = shiftLeft(
                slots = slots,
                fromSlotNumber = slotNumber,
                now = System.currentTimeMillis()
            )
            recordOperation(
                type = StockOperationType.REMOVE_AT,
                context = OperationContext(container, removed),
                componentId = removedStock?.componentId,
                quantityDelta = removedStock?.quantity?.let { -it } ?: 0,
                bleOpcode = bleOpcode(container, SmartChassisBindingOp.REMOVE_AT)
            )
            SlotOperationResult(true, affectedSlots = changed)
        }
    }

    override suspend fun moveBlock(
        containerId: Long,
        fromSlotNumber: Int,
        toSlotNumber: Int,
        length: Int
    ): SlotOperationResult {
        return database.withTransaction {
            val container = containerDao.findContainerById(containerId)?.toStockContainer()
                ?: return@withTransaction SlotOperationResult(false, ERROR_CONTAINER_NOT_FOUND)
            if (container.type == ContainerType.LEGACY_LOCATION) {
                return@withTransaction SlotOperationResult(false, ERROR_UNSUPPORTED_OPERATION)
            }
            if (!isValidBlock(container.slotCount, fromSlotNumber, toSlotNumber, length)) {
                return@withTransaction SlotOperationResult(false, ERROR_INVALID_BLOCK)
            }
            val slots = stockPlacementRepository.getContainerSlotStock(containerId)
            if (slots.size < container.slotCount || fromSlotNumber == toSlotNumber) {
                return@withTransaction SlotOperationResult(true)
            }
            val changed = moveSlotStockBlock(
                slots = slots,
                fromSlotNumber = fromSlotNumber,
                toSlotNumber = toSlotNumber,
                length = length,
                now = System.currentTimeMillis()
            )
            val source = slots.first { it.slot.slotNumber == fromSlotNumber }
            recordOperation(
                type = StockOperationType.MOVE_BLOCK,
                context = OperationContext(container, source),
                rawPayload = "from=$fromSlotNumber;to=$toSlotNumber;length=$length",
                bleOpcode = bleOpcode(container, SmartChassisBindingOp.MOVE_BLOCK)
            )
            SlotOperationResult(true, affectedSlots = changed)
        }
    }

    override suspend fun setQuantity(containerId: Long, slotNumber: Int, quantity: Int): SlotOperationResult {
        if (quantity < 0) {
            return SlotOperationResult(false, ERROR_INVALID_QUANTITY)
        }
        return database.withTransaction {
            val context = resolveContext(containerId, slotNumber)
                ?: return@withTransaction SlotOperationResult(false, ERROR_SLOT_NOT_FOUND)
            val stock = stockPlacementRepository.findSlotStock(context.slot.slot.id)?.stockItem
                ?: return@withTransaction SlotOperationResult(false, ERROR_EMPTY_SLOT)
            stockPlacementRepository.updateStockQuantity(
                stockItemId = stock.id,
                quantity = quantity,
                updatedAt = System.currentTimeMillis()
            )
            recordOperation(
                type = StockOperationType.SET_QTY,
                context = context,
                componentId = stock.componentId,
                quantityDelta = quantity - stock.quantity,
                bleOpcode = bleOpcode(context.container, SmartChassisBindingOp.SET_QTY)
            )
            SlotOperationResult(
                success = true,
                affectedSlots = listOfNotNull(stockPlacementRepository.findSlotStock(context.slot.slot.id))
            )
        }
    }

    override suspend fun resolveLocalComponent(partIdOrNumber: String): ComponentDetail? {
        val normalized = partIdOrNumber.trim().uppercase(Locale.ROOT)
        if (normalized.isBlank()) {
            return null
        }
        val existing = componentDao.findByProtocolPartId(normalized)
            ?: componentDao.findByPartNumber(normalized)
        if (existing != null) {
            return existing.toComponentDetail()
        }
        val now = System.currentTimeMillis()
        val component = ComponentEntity(
            partNumber = normalized,
            protocolPartId = protocolPartIdForComponent(null, normalized, "SMART_SLOT_INBOUND"),
            name = normalized,
            updatedAt = now
        )
        val insertedId = componentDao.insert(component)
        val resolved = if (insertedId > 0) {
            component.copy(
                id = insertedId,
                protocolPartId = protocolPartIdForComponent(insertedId, normalized, "SMART_SLOT_INBOUND")
            ).also { inserted ->
                if (inserted.protocolPartId != component.protocolPartId) {
                    componentDao.update(inserted)
                }
            }
        } else {
            componentDao.findByPartNumber(normalized)
                ?: componentDao.findByProtocolPartId(normalized)
                ?: return null
        }
        return resolved.toComponentDetail()
    }

    override suspend fun findContainer(containerId: Long): StockContainer? {
        return containerDao.findContainerById(containerId)?.toStockContainer()
    }

    private suspend fun resolveContext(containerId: Long, slotNumber: Int): OperationContext? {
        val container = containerDao.findContainerById(containerId)?.toStockContainer() ?: return null
        val slot = stockPlacementRepository
            .getContainerSlotStock(containerId)
            .firstOrNull { it.slot.slotNumber == slotNumber }
            ?: return null
        return OperationContext(container, slot)
    }

    private fun OperationContext.supportsSingleSlotWrite(): Boolean {
        return container.type != ContainerType.LEGACY_LOCATION || slot.slot.slotNumber == 1
    }

    private suspend fun replaceSlotStock(
        context: OperationContext,
        componentId: Long,
        quantity: Int,
        operationType: StockOperationType,
        sourceType: String?,
        sourceRef: String?,
        rawPayload: String?,
        bleOpcode: Int?
    ) {
        val existing = context.slot.stockItem
        val now = System.currentTimeMillis()
        stockPlacementRepository.replaceSlotStock(
            StockPlacementWrite(
                containerId = context.container.id,
                slotId = context.slot.slot.id,
                componentId = componentId,
                quantity = quantity,
                lastInboundAt = now,
                updatedAt = now
            )
        )
        recordOperation(
            type = operationType,
            context = context,
            componentId = componentId,
            quantityDelta = quantity - (existing?.quantity ?: 0),
            sourceType = sourceType,
            sourceRef = sourceRef,
            rawPayload = rawPayload,
            bleOpcode = bleOpcode
        )
    }

    private suspend fun shiftRight(
        slots: List<ContainerSlotStock>,
        fromSlotNumber: Int,
        now: Long
    ): List<ContainerSlotStock> {
        val byNumber = slots.associateBy { it.slot.slotNumber }
        val changed = mutableListOf<ContainerSlotStock>()
        for (slotNumber in slots.size downTo fromSlotNumber + 1) {
            val target = byNumber[slotNumber] ?: continue
            val source = byNumber[slotNumber - 1] ?: continue
            copyStock(source, target, now)
            stockPlacementRepository.findSlotStock(target.slot.id)?.let(changed::add)
        }
        val target = byNumber[fromSlotNumber] ?: return changed
        stockPlacementRepository.deleteSlotStock(target.slot.id)
        stockPlacementRepository.findSlotStock(target.slot.id)?.let(changed::add)
        return changed
    }

    private suspend fun shiftLeft(
        slots: List<ContainerSlotStock>,
        fromSlotNumber: Int,
        now: Long
    ): List<ContainerSlotStock> {
        val byNumber = slots.associateBy { it.slot.slotNumber }
        val changed = mutableListOf<ContainerSlotStock>()
        for (slotNumber in fromSlotNumber until slots.size) {
            val target = byNumber[slotNumber] ?: continue
            val source = byNumber[slotNumber + 1] ?: continue
            copyStock(source, target, now)
            stockPlacementRepository.findSlotStock(target.slot.id)?.let(changed::add)
        }
        val last = byNumber[slots.size] ?: return changed
        stockPlacementRepository.deleteSlotStock(last.slot.id)
        stockPlacementRepository.findSlotStock(last.slot.id)?.let(changed::add)
        return changed
    }

    private suspend fun moveSlotStockBlock(
        slots: List<ContainerSlotStock>,
        fromSlotNumber: Int,
        toSlotNumber: Int,
        length: Int,
        now: Long
    ): List<ContainerSlotStock> {
        val current = slots.sortedBy { it.slot.slotNumber }
        val block = current.subList(fromSlotNumber - 1, fromSlotNumber - 1 + length)
        val rest = current.filterIndexed { index, _ ->
            val slotNumber = index + 1
            slotNumber < fromSlotNumber || slotNumber >= fromSlotNumber + length
        }
        val insertIndex = (toSlotNumber - 1).coerceIn(0, rest.size)
        val reordered = buildList {
            addAll(rest.take(insertIndex))
            addAll(block)
            addAll(rest.drop(insertIndex))
        }
        val changed = mutableListOf<ContainerSlotStock>()
        current.zip(reordered).forEach { (targetSlot, sourceSlot) ->
            copyStock(sourceSlot, targetSlot, now)
            stockPlacementRepository.findSlotStock(targetSlot.slot.id)?.let(changed::add)
        }
        return changed
    }

    private suspend fun copyStock(
        source: ContainerSlotStock,
        target: ContainerSlotStock,
        now: Long
    ) {
        val sourceStock = source.stockItem
        if (sourceStock == null) {
            stockPlacementRepository.deleteSlotStock(target.slot.id)
            return
        }
        stockPlacementRepository.replaceSlotStock(
            StockPlacementWrite(
                containerId = target.slot.containerId,
                slotId = target.slot.id,
                componentId = sourceStock.componentId,
                quantity = sourceStock.quantity,
                quantityState = sourceStock.quantityState,
                safetyStockThreshold = sourceStock.safetyStockThreshold,
                lastInboundAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun recordOperation(
        type: StockOperationType,
        context: OperationContext,
        componentId: Long? = null,
        quantityDelta: Int = 0,
        sourceType: String? = null,
        sourceRef: String? = null,
        rawPayload: String? = null,
        bleOpcode: Int? = null
    ) {
        stockPlacementRepository.recordOperation(
            StockOperation(
                type = type,
                containerId = context.container.id,
                slotId = context.slot.slot.id,
                componentId = componentId,
                quantityDelta = quantityDelta,
                sourceType = sourceType,
                sourceRef = sourceRef,
                rawPayload = rawPayload,
                bleOpcode = bleOpcode,
                tableSeqBefore = context.container.tableSeq,
                tableSeqAfter = context.container.tableSeq,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun upsertComponent(component: ComponentDetail, sourceType: String): ComponentEntity {
        val normalizedPartNumber = component.partNumber.trim().uppercase(Locale.ROOT)
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        val specJson = component.specifications
            .takeIf { it.isNotEmpty() }
            ?.let { JSONObject(it).toString() }
        val now = System.currentTimeMillis()
        if (existing == null) {
            val entity = ComponentEntity(
                partNumber = normalizedPartNumber,
                protocolPartId = protocolPartIdForComponent(null, normalizedPartNumber, sourceType),
                mpn = component.mpn,
                name = component.name,
                brand = component.brand,
                packageName = component.packageName,
                category = component.category,
                specJson = specJson,
                description = component.description,
                sourceUrl = component.productUrl,
                imageLocalPath = component.imageLocalPath,
                updatedAt = now
            )
            val insertedId = componentDao.insert(entity)
            val inserted = if (insertedId > 0) {
                entity.copy(
                    id = insertedId,
                    protocolPartId = protocolPartIdForComponent(insertedId, normalizedPartNumber, sourceType)
                )
            } else {
                componentDao.findByPartNumber(normalizedPartNumber)
                    ?: error("Failed to resolve component $normalizedPartNumber")
            }
            if (inserted.protocolPartId != entity.protocolPartId) {
                componentDao.update(inserted)
            }
            return inserted
        }
        val updated = existing.copy(
            protocolPartId = existing.protocolPartId
                ?: protocolPartIdForComponent(existing.id, normalizedPartNumber, sourceType),
            mpn = component.mpn ?: existing.mpn,
            name = component.name ?: existing.name,
            brand = component.brand ?: existing.brand,
            packageName = component.packageName ?: existing.packageName,
            category = component.category ?: existing.category,
            specJson = specJson ?: existing.specJson,
            description = component.description ?: existing.description,
            sourceUrl = component.productUrl ?: existing.sourceUrl,
            imageLocalPath = component.imageLocalPath ?: existing.imageLocalPath,
            updatedAt = now
        )
        if (updated != existing) {
            componentDao.update(updated)
        }
        return updated
    }

    private fun protocolPartIdForComponent(
        componentId: Long?,
        partNumber: String,
        sourceType: String
    ): String? {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        val isManual = sourceType == "MANUAL_INPUT" ||
            sourceType == "SMART_SLOT_INBOUND" ||
            normalizedPartNumber.matches(MANUAL_INBOUND_PART_NUMBER_REGEX)
        if (!isManual && normalizedPartNumber.matches(PROTOCOL_PART_ID_REGEX)) {
            return normalizedPartNumber
        }
        if (normalizedPartNumber.matches(PROTOCOL_PART_ID_REGEX)) {
            return normalizedPartNumber
        }
        return componentId?.let { id -> "M%09d".format(Locale.ROOT, id) }
    }

    private fun isValidBlock(slotCount: Int, from: Int, to: Int, length: Int): Boolean {
        return from in 1..slotCount &&
            to in 1..slotCount &&
            length in 1..slotCount &&
            from + length - 1 <= slotCount &&
            to + length - 1 <= slotCount
    }

    private fun bleOpcode(container: StockContainer, op: SmartChassisBindingOp): Int? {
        return if (container.type == ContainerType.SMART_CHASSIS) op.code else null
    }

    private fun com.example.lcsc_android_erp.core.database.entity.ContainerEntity.toStockContainer(): StockContainer {
        return StockContainer(
            id = id,
            code = code,
            displayName = displayName,
            type = type.toContainerType(),
            slotCount = slotCount,
            colorHex = colorHex,
            sortMode = sortMode,
            remark = remark,
            createdAt = createdAt,
            updatedAt = updatedAt,
            macAddress = macAddress,
            batchId = batchId,
            protoVersion = protoVersion,
            firmwareVersion = firmwareVersion,
            hardwareVersion = hardwareVersion,
            batteryPct = batteryPct,
            statusFlags = statusFlags,
            tableSeq = tableSeq,
            tableCrc16 = tableCrc16,
            lastSeenAt = lastSeenAt,
            lastSyncedAt = lastSyncedAt
        )
    }

    private fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }

    private fun ComponentEntity.toComponentDetail(): ComponentDetail {
        return ComponentDetail(
            partNumber = partNumber,
            mpn = mpn,
            name = name,
            brand = brand,
            packageName = packageName,
            category = category,
            description = description,
            stockQuantity = null,
            price = null,
            productUrl = sourceUrl,
            datasheetUrl = null,
            imageLocalPath = imageLocalPath,
            imageUrl = null,
            specifications = parseSpecifications(specJson)
        )
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

    private data class OperationContext(
        val container: StockContainer,
        val slot: ContainerSlotStock
    )

    private companion object {
        private const val ERROR_CONTAINER_NOT_FOUND = "container_not_found"
        private const val ERROR_SLOT_NOT_FOUND = "slot_not_found"
        private const val ERROR_EMPTY_SLOT = "empty_slot"
        private const val ERROR_CONTAINER_FULL = "container_full"
        private const val ERROR_INVALID_BLOCK = "invalid_block"
        private const val ERROR_INVALID_QUANTITY = "invalid_quantity"
        private const val ERROR_UNSUPPORTED_OPERATION = "unsupported_operation"
        private val PROTOCOL_PART_ID_REGEX = Regex("^[CM][A-Z0-9]{0,9}$")
        private val MANUAL_INBOUND_PART_NUMBER_REGEX = Regex("^C0\\d+$")
    }
}
