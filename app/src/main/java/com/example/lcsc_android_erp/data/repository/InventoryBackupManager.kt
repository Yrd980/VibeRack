package com.example.lcsc_android_erp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.InventoryItemDao
import com.example.lcsc_android_erp.core.database.dao.InventoryTransactionDao
import com.example.lcsc_android_erp.core.database.dao.StorageLocationDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryTransactionEntity
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject

class InventoryBackupManager(
    private val context: Context,
    private val database: RoomDatabase,
    private val storageLocationDao: StorageLocationDao,
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val componentImageStore: ComponentImageStore
) {
    private data class ImportedComponentRow(
        val entity: ComponentEntity,
        val requiresEnrichment: Boolean
    )

    private data class ImportedComponentImage(
        val fileName: String?,
        val bytes: ByteArray
    )

    suspend fun exportToUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = XSSFWorkbook()

            val storageLocations = database.withTransaction { storageLocationDao.getAll() }
            val initialComponents = database.withTransaction { componentDao.getAll() }
            val missingDetails = initialComponents
                .filter(::requiresExportEnrichment)
                .map(ComponentEntity::partNumber)
            if (missingDetails.isNotEmpty()) {
                componentEnrichmentManager.enrichNow(missingDetails)
            }
            val components = database.withTransaction { componentDao.getAll() }
            val inventoryItems = database.withTransaction { inventoryItemDao.getAll() }
            val inventoryTransactions = database.withTransaction { inventoryTransactionDao.getAll() }

            workbook.createSheet("meta").apply {
                writeRow(
                    0,
                    listOf("schemaVersion", "1")
                )
            }

            workbook.createSheet("storage_locations").apply {
                writeRow(0, listOf("id", "code", "displayName", "colorHex", "sortMode", "remark", "createdAt"))
                storageLocations.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.code,
                            item.displayName,
                            item.colorHex,
                            item.sortMode,
                            item.remark,
                            item.createdAt
                        )
                    )
                }
            }

            workbook.createSheet("components").apply {
                val imageColumnIndex = 12
                writeRow(
                    0,
                    listOf(
                        "id",
                        "partNumber",
                        "mpn",
                        "name",
                        "brand",
                        "packageName",
                        "category",
                        "specJson",
                        "description",
                        "sourceUrl",
                        "updatedAt",
                        "imagePreview"
                    )
                )
                components.forEachIndexed { index, item ->
                    val rowIndex = index + 1
                    writeRow(
                        rowIndex,
                        listOf(
                            item.id,
                            item.partNumber,
                            item.mpn,
                            item.name,
                            item.brand,
                            item.packageName,
                            item.category,
                            item.specJson,
                            item.description,
                            item.sourceUrl,
                            item.updatedAt,
                            null
                        )
                    )
                    insertComponentPreviewImage(
                        workbook = workbook,
                        rowIndex = rowIndex,
                        imageColumnIndex = imageColumnIndex,
                        imageLocalPath = item.imageLocalPath
                    )
                }
                setColumnWidth(imageColumnIndex, 18 * 256)
            }

            workbook.createSheet("component_images").apply {
                writeRow(0, listOf("partNumber", "fileName", "contentBase64"))
                components.forEachIndexed { index, item ->
                    val imageFile = item.imageLocalPath
                        ?.let(::File)
                        ?.takeIf { it.exists() && it.length() > 0L }
                    writeRow(
                        index + 1,
                        listOf(
                            item.partNumber,
                            imageFile?.name,
                            imageFile?.readBytes()?.let { bytes ->
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                            }
                        )
                    )
                }
            }

            workbook.createSheet("inventory_items").apply {
                writeRow(0, listOf("id", "componentId", "locationId", "quantity", "lastInboundAt", "updatedAt"))
                inventoryItems.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.componentId,
                            item.locationId,
                            item.quantity,
                            item.lastInboundAt,
                            item.updatedAt
                        )
                    )
                }
            }

            workbook.createSheet("inventory_transactions").apply {
                writeRow(
                    0,
                    listOf(
                        "id",
                        "componentId",
                        "locationId",
                        "txnType",
                        "quantityDelta",
                        "sourceType",
                        "sourceRef",
                        "rawPayload",
                        "createdAt"
                    )
                )
                inventoryTransactions.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.componentId,
                            item.locationId,
                            item.txnType,
                            item.quantityDelta,
                            item.sourceType,
                            item.sourceRef,
                            item.rawPayload,
                            item.createdAt
                        )
                    )
                }
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                workbook.use { it.write(outputStream) }
            } ?: throw IOException(context.getString(R.string.settings_backup_open_export_failed))

            null
        }.getOrElse { throwable ->
            throwable.message ?: context.getString(R.string.settings_backup_export_failed)
        }
    }

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                WorkbookFactory.create(inputStream)
            } ?: throw IOException(context.getString(R.string.settings_backup_open_import_failed))

            workbook.use { wb ->
                val schemaVersion = wb.getSheet("meta")
                    ?.getRow(0)
                    ?.getCell(1)
                    ?.asString()
                    ?.toIntOrNull()
                    ?: return@use context.getString(R.string.settings_backup_unsupported_version)
                if (schemaVersion != 1) {
                    return@use context.getString(R.string.settings_backup_unsupported_version)
                }

                val storageLocations = wb.getSheet("storage_locations").toStorageLocations()
                val componentImages = wb.getSheet("component_images").toComponentImages()
                val importedComponents = wb.getSheet("components").toComponents(componentImages)
                val components = importedComponents.map { it.entity }
                val inventoryItems = wb.getSheet("inventory_items").toInventoryItems()
                val inventoryTransactions = wb.getSheet("inventory_transactions").toInventoryTransactions()

                database.withTransaction {
                    inventoryTransactionDao.deleteAll()
                    inventoryItemDao.deleteAll()
                    componentDao.deleteAll()
                    storageLocationDao.deleteAll()

                    if (storageLocations.isNotEmpty()) {
                        storageLocationDao.insertAll(storageLocations)
                    }
                    if (components.isNotEmpty()) {
                        componentDao.insertAll(components)
                    }
                    if (inventoryItems.isNotEmpty()) {
                        inventoryItemDao.insertAll(inventoryItems)
                    }
                    if (inventoryTransactions.isNotEmpty()) {
                        inventoryTransactionDao.insertAll(inventoryTransactions)
                    }
                }
                componentEnrichmentManager.scheduleAll(
                    importedComponents
                        .filter { it.requiresEnrichment }
                        .map { it.entity.partNumber }
                )
                null
            }
        }.getOrElse { throwable ->
            throwable.message ?: context.getString(R.string.settings_backup_import_failed)
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet.writeRow(
        rowIndex: Int,
        values: List<Any?>
    ) {
        val row = createRow(rowIndex)
        values.forEachIndexed { columnIndex, value ->
            val cell = row.createCell(columnIndex)
            when (value) {
                null -> cell.setBlank()
                is Number -> cell.setCellValue(value.toDouble())
                is Boolean -> cell.setCellValue(value)
                else -> cell.setCellValue(value.toString())
            }
        }
    }

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

    private suspend fun org.apache.poi.ss.usermodel.Sheet?.toComponents(
        componentImages: Map<String, ImportedComponentImage>
    ): List<ImportedComponentRow> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            val partNumber = row.string("partNumber").orEmpty().trim().uppercase()
            val restoredImagePath = componentImages[partNumber]?.let { image ->
                componentImageStore.persistImageBytes(
                    partNumber = partNumber,
                    sourceName = image.fileName,
                    bytes = image.bytes
                )
            }
            ImportedComponentRow(
                entity = ComponentEntity(
                    id = row.long("id"),
                    partNumber = partNumber,
                    mpn = row.stringOrNull("mpn"),
                    name = row.stringOrNull("name"),
                    brand = row.stringOrNull("brand"),
                    packageName = row.stringOrNull("packageName"),
                    category = row.stringOrNull("category"),
                    specJson = row.stringOrNull("specJson"),
                    description = row.stringOrNull("description"),
                    sourceUrl = row.stringOrNull("sourceUrl"),
                    imageLocalPath = restoredImagePath,
                    updatedAt = row.long("updatedAt")
                ),
                requiresEnrichment = false
            )
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet?.toComponentImages(): Map<String, ImportedComponentImage> {
        val sheet = this ?: return emptyMap()
        return sheet.dataRows().mapNotNull { row ->
            val partNumber = row.string("partNumber")?.trim()?.uppercase().orEmpty()
            val contentBase64 = row.stringOrNull("contentBase64")
            if (partNumber.isBlank() || contentBase64.isNullOrBlank()) {
                null
            } else {
                val bytes = runCatching {
                    Base64.decode(contentBase64, Base64.DEFAULT)
                }.getOrNull() ?: return@mapNotNull null
                partNumber to ImportedComponentImage(
                    fileName = row.stringOrNull("fileName"),
                    bytes = bytes
                )
            }
        }.toMap()
    }

    private fun specificationCount(specJson: String?): Int {
        if (specJson.isNullOrBlank()) {
            return 0
        }
        return runCatching {
            val json = JSONObject(specJson)
            json.keys().asSequence()
                .map { key -> json.optString(key).trim() }
                .count { value -> value.isNotBlank() && value != "null" }
        }.getOrDefault(0)
    }

    private fun requiresExportEnrichment(component: ComponentEntity): Boolean {
        val hasImage = component.imageLocalPath
            ?.let(::File)
            ?.let { it.exists() && it.length() > 0L }
            ?: false
        return component.name.isNullOrBlank() ||
            specificationCount(component.specJson) == 0 ||
            !hasImage
    }

    private fun insertComponentPreviewImage(
        workbook: Workbook,
        rowIndex: Int,
        imageColumnIndex: Int,
        imageLocalPath: String?
    ) {
        val imageFile = imageLocalPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?: return
        val pictureType = when (imageFile.extension.lowercase()) {
            "png" -> Workbook.PICTURE_TYPE_PNG
            else -> Workbook.PICTURE_TYPE_JPEG
        }
        val bytes = runCatching { imageFile.readBytes() }.getOrNull() ?: return
        val sheet = workbook.getSheet("components") ?: return
        val drawing = sheet.createDrawingPatriarch()
        val helper = workbook.creationHelper
        val anchor = helper.createClientAnchor().apply {
            setCol1(imageColumnIndex)
            setRow1(rowIndex)
            setCol2(imageColumnIndex + 1)
            setRow2(rowIndex + 1)
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }
        val pictureIndex = workbook.addPicture(bytes, pictureType)
        drawing.createPicture(anchor, pictureIndex)
        sheet.getRow(rowIndex)?.heightInPoints = 72f
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

    private fun org.apache.poi.ss.usermodel.Sheet?.toInventoryTransactions(): List<InventoryTransactionEntity> {
        val sheet = this ?: return emptyList()
        return sheet.dataRows().map { row ->
            InventoryTransactionEntity(
                id = row.long("id"),
                componentId = row.long("componentId"),
                locationId = row.long("locationId"),
                txnType = row.string("txnType").orEmpty(),
                quantityDelta = row.int("quantityDelta"),
                sourceType = row.string("sourceType").orEmpty(),
                sourceRef = row.stringOrNull("sourceRef"),
                rawPayload = row.stringOrNull("rawPayload"),
                createdAt = row.long("createdAt")
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
        fun string(header: String): String? {
            val cellIndex = headers[header] ?: return null
            return row.getCell(cellIndex)?.asString()
        }
        fun stringOrNull(header: String): String? = string(header)?.takeIf { it.isNotBlank() }
        fun long(header: String): Long = string(header)?.toLongOrNull() ?: 0L
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
