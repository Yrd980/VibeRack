package com.viberack.app.data.repository

import com.viberack.app.core.database.entity.BoxEntity
import com.viberack.app.core.database.entity.BoxLayerEntity
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity
import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.StorageLocationSortMode
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet

internal class InventoryBackupParser(
    private val componentImageStore: ComponentImageStore,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) {
    data class ParsedBackup(
        val storageLocations: List<StorageLocationEntity>,
        val recentLocationColors: List<String>,
        val importedComponents: List<ImportedComponentRow>,
        val inventoryItems: List<InventoryItemEntity>,
        val boxes: List<BoxEntity>,
        val boxLayers: List<BoxLayerEntity>,
        val containers: List<ContainerEntity>,
        val containerSlots: List<ContainerSlotEntity>,
        val stockItems: List<StockItemEntity>,
        val stockOperations: List<StockOperationEntity>
    )

    suspend fun parse(workbook: Workbook): ParsedBackup {
        val metaSheet = workbook.getSheet("meta")
        val componentSheet = workbook.getSheet("components")
        val importedComponents = componentSheet.toComponents(componentSheet.extractPreviewImagesByRow())
        return ParsedBackup(
            storageLocations = workbook.getSheet("storage_locations").toStorageLocations(),
            recentLocationColors = metaSheet.toRecentLocationColors(),
            importedComponents = importedComponents,
            inventoryItems = workbook.getSheet("inventory_items").toInventoryItems(),
            boxes = workbook.getSheet("boxes").toBoxes(),
            boxLayers = workbook.getSheet("box_layers").toBoxLayers(),
            containers = workbook.getSheet("containers").toContainers(),
            containerSlots = workbook.getSheet("container_slots").toContainerSlots(),
            stockItems = workbook.getSheet("stock_items").toStockItems(),
            stockOperations = workbook.getSheet("stock_operations").toStockOperations()
        )
    }

    fun schemaVersion(workbook: Workbook): Int? {
        return workbook.getSheet("meta")
            ?.getRow(0)
            ?.getCell(1)
            ?.asString()
            ?.toIntOrNull()
    }

    data class ImportedComponentRow(
        val entity: ComponentEntity,
        val requiresEnrichment: Boolean
    )

    private data class ImportedSheetImage(
        val bytes: ByteArray,
        val sourceName: String?
    )

    private fun org.apache.poi.ss.usermodel.Sheet?.toStorageLocations(): List<StorageLocationEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            StorageLocationEntity(
                id = row.long("id"),
                code = row.string("code").orEmpty(),
                displayName = row.stringOrNull("displayName"),
                colorHex = row.stringOrNull("colorHex"),
                sortMode = row.stringOrNull("sortMode") ?: StorageLocationSortMode.NONE,
                remark = row.stringOrNull("remark"),
                createdAt = row.long("createdAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toBoxes(): List<BoxEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            BoxEntity(
                id = row.long("id"),
                code = row.string("code").orEmpty(),
                name = row.stringOrNull("name"),
                layerCount = row.int("layerCount"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toBoxLayers(): List<BoxLayerEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            BoxLayerEntity(
                id = row.long("id"),
                boxId = row.long("boxId"),
                layerCode = row.string("layerCode").orEmpty(),
                displayName = row.stringOrNull("displayName"),
                sortOrder = row.int("sortOrder"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toContainers(): List<ContainerEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            ContainerEntity(
                id = row.long("id"),
                code = row.string("code").orEmpty(),
                displayName = row.stringOrNull("displayName"),
                type = row.stringOrNull("type") ?: ContainerType.LEGACY_LOCATION.name,
                slotCount = row.int("slotCount"),
                colorHex = row.stringOrNull("colorHex"),
                sortMode = row.stringOrNull("sortMode") ?: StorageLocationSortMode.NONE,
                remark = row.stringOrNull("remark"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt"),
                macAddress = row.stringOrNull("macAddress"),
                batchId = row.intOrNull("batchId"),
                protoVersion = row.intOrNull("protoVersion"),
                firmwareVersion = row.stringOrNull("firmwareVersion"),
                hardwareVersion = row.stringOrNull("hardwareVersion"),
                batteryPct = row.intOrNull("batteryPct"),
                statusFlags = row.intOrNull("statusFlags"),
                tableSeq = row.longOrNull("tableSeq"),
                tableCrc16 = row.intOrNull("tableCrc16"),
                lastSeenAt = row.longOrNull("lastSeenAt"),
                lastSyncedAt = row.longOrNull("lastSyncedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toContainerSlots(): List<ContainerSlotEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            ContainerSlotEntity(
                id = row.long("id"),
                containerId = row.long("containerId"),
                slotNumber = row.int("slotNumber"),
                slotCode = row.string("slotCode").orEmpty(),
                displayName = row.stringOrNull("displayName"),
                sortOrder = row.int("sortOrder"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toStockItems(): List<StockItemEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            StockItemEntity(
                id = row.long("id"),
                componentId = row.long("componentId"),
                containerId = row.long("containerId"),
                containerSlotId = row.long("containerSlotId"),
                quantity = row.int("quantity"),
                quantityState = row.stringOrNull("quantityState") ?: QuantityState.KNOWN.name,
                safetyStockThreshold = row.intOrNull("safetyStockThreshold"),
                lastInboundAt = row.long("lastInboundAt"),
                updatedAt = row.long("updatedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toStockOperations(): List<StockOperationEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            StockOperationEntity(
                id = row.long("id"),
                type = row.string("type").orEmpty(),
                containerId = row.longOrNull("containerId"),
                containerSlotId = row.longOrNull("containerSlotId"),
                componentId = row.longOrNull("componentId"),
                quantityDelta = row.int("quantityDelta"),
                sourceType = row.stringOrNull("sourceType"),
                sourceRef = row.stringOrNull("sourceRef"),
                rawPayload = row.stringOrNull("rawPayload"),
                bleOpcode = row.intOrNull("bleOpcode"),
                bleStatus = row.intOrNull("bleStatus"),
                tableSeqBefore = row.longOrNull("tableSeqBefore"),
                tableSeqAfter = row.longOrNull("tableSeqAfter"),
                createdAt = row.long("createdAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toRecentLocationColors(): List<String> {
        val sheet = this ?: return emptyList()
        val rawValue = (0..sheet.lastRowNum)
            .asSequence()
            .mapNotNull { rowIndex -> sheet.getRow(rowIndex) }
            .firstOrNull { row ->
                row.getCell(0)?.asString()?.trim() == "recentLocationColors"
            }
            ?.getCell(1)
            ?.asString()
            ?: return emptyList()
        return rawValue
            .split('\n')
            .map(String::trim)
            .filter { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
            .map(String::uppercase)
            .distinct()
            .take(5)
    }

    private suspend fun org.apache.poi.ss.usermodel.Sheet?.toComponents(
        previewImagesByRow: Map<Int, ImportedSheetImage>
    ): List<ImportedComponentRow> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            val partNumber = row.string("partNumber").orEmpty().trim().uppercase()
            val imageLocalPath = previewImagesByRow[row.rowIndex]
                ?.takeIf { partNumber.isNotBlank() }
                ?.let { previewImage ->
                    componentImageStore.persistImageBytes(
                        partNumber = partNumber,
                        sourceName = previewImage.sourceName,
                        bytes = previewImage.bytes
                    )
                }
            ImportedComponentRow(
                entity = ComponentEntity(
                    id = row.long("id"),
                    partNumber = partNumber,
                    protocolPartId = protocolPartIdStrategy.forComponent(
                        componentId = row.long("id"),
                        partNumber = partNumber
                    ),
                    mpn = null,
                    name = row.stringOrNull("name"),
                    brand = row.stringOrNull("brand"),
                    packageName = row.stringOrNull("packageName"),
                    category = row.stringOrNull("category"),
                    specJson = row.stringOrNull("specJson"),
                    description = row.stringOrNull("description"),
                    sourceUrl = row.stringOrNull("sourceUrl"),
                    imageLocalPath = imageLocalPath,
                    updatedAt = row.long("updatedAt")
                ),
                requiresEnrichment = false
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.extractPreviewImagesByRow(): Map<Int, ImportedSheetImage> {
        val sheet = this as? XSSFSheet ?: return emptyMap()
        val drawing = sheet.drawingPatriarch ?: return emptyMap()
        val imagesByRow = linkedMapOf<Int, ImportedSheetImage>()
        drawing.shapes.forEach { shape ->
            val picture = shape as? XSSFPicture ?: return@forEach
            val anchor = picture.anchor as? XSSFClientAnchor ?: return@forEach
            val pictureData = picture.pictureData ?: return@forEach
            val rowIndex = anchor.row1.toInt()
            if (rowIndex <= 0) {
                return@forEach
            }
            val extension = pictureData.suggestFileExtension()
                ?.takeIf { it.isNotBlank() }
                ?: "jpg"
            imagesByRow[rowIndex] = ImportedSheetImage(
                bytes = pictureData.data,
                sourceName = "preview.$extension"
            )
        }
        return imagesByRow
    }




    private fun org.apache.poi.ss.usermodel.Sheet?.toInventoryItems(): List<InventoryItemEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            InventoryItemEntity(
                id = row.long("id"),
                componentId = row.long("componentId"),
                locationId = row.long("locationId"),
                quantity = row.int("quantity"),
                lastInboundAt = row.long("lastInboundAt"),
                updatedAt = row.long("updatedAt")
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet.dataRows(): List<ExcelRow> {
        if (physicalNumberOfRows <= 1) {
            return emptyList()
        }
        val headerRow = getRow(0) ?: return emptyList()
        val headers = buildMap {
            for (cellIndex in 0 until headerRow.lastCellNum) {
                val header = headerRow.getCell(cellIndex.toInt())?.asString()?.trim().orEmpty()
                if (header.isNotEmpty()) {
                    put(header, cellIndex.toInt())
                }
            }
        }
        return buildList {
            for (rowIndex in 1..lastRowNum) {
                val row = getRow(rowIndex) ?: continue
                if (row.isEmptyRow()) continue
                add(ExcelRow(headers, row))
            }
        }
    }

    private inner class ExcelRow(
        val headers: Map<String, Int>,
        val row: Row
    ) {
        val rowIndex: Int get() = row.rowNum

        fun string(header: String): String? {
            val cellIndex = headers[header] ?: return null
            return row.getCell(cellIndex)?.asString()
        }
        fun stringOrNull(header: String): String? = string(header)?.takeIf { it.isNotBlank() }
        fun longOrNull(header: String): Long? = string(header)?.toLongOrNull()
        fun long(header: String): Long = string(header)?.toLongOrNull() ?: 0L
        fun intOrNull(header: String): Int? = string(header)?.toIntOrNull()
        fun int(header: String): Int = string(header)?.toIntOrNull() ?: 0
    }

    private fun Row.isEmptyRow(): Boolean {
        for (cellIndex in 0 until lastCellNum) {
            val value = getCell(cellIndex.toInt())?.asString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                return false
            }
        }
        return true
    }

    private fun Cell.asString(): String {
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue.toLong().toString()
            CellType.BOOLEAN -> booleanCellValue.toString()
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.NUMERIC -> numericCellValue.toLong().toString()
                CellType.BOOLEAN -> booleanCellValue.toString()
                else -> stringCellValue.orEmpty()
            }
            else -> stringCellValue.orEmpty()
        }
    }
}
