package com.example.lcsc_android_erp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.database.dao.ComponentDao
import com.example.lcsc_android_erp.core.database.dao.ContainerDao
import com.example.lcsc_android_erp.core.database.dao.InventoryItemDao
import com.example.lcsc_android_erp.core.database.dao.InventoryTransactionDao
import com.example.lcsc_android_erp.core.database.dao.StockItemDao
import com.example.lcsc_android_erp.core.database.dao.StockOperationDao
import com.example.lcsc_android_erp.core.database.dao.StorageLocationDao
import com.example.lcsc_android_erp.core.database.entity.ComponentEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerEntity
import com.example.lcsc_android_erp.core.database.entity.ContainerSlotEntity
import com.example.lcsc_android_erp.core.database.entity.InventoryItemEntity
import com.example.lcsc_android_erp.core.database.entity.StockItemEntity
import com.example.lcsc_android_erp.core.database.entity.StorageLocationEntity
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.LocationCategoryProfile
import com.example.lcsc_android_erp.domain.model.QuantityState
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode
import com.example.lcsc_android_erp.domain.model.calculateDominantLocationCategoryProfile
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject

class InventoryBackupManager(
    private val context: Context,
    private val database: RoomDatabase,
    private val storageLocationDao: StorageLocationDao,
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val containerDao: ContainerDao,
    private val stockItemDao: StockItemDao,
    private val stockOperationDao: StockOperationDao,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val componentImageStore: ComponentImageStore,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private companion object {
        const val TAG = "InventoryBackupManager"
        val PROTOCOL_PART_ID_REGEX = Regex("^[CM][A-Z0-9]{0,9}$")
        val MANUAL_INBOUND_PART_NUMBER_REGEX = Regex("^C0\\d+$")
    }

    private data class ImportedComponentRow(
        val entity: ComponentEntity,
        val requiresEnrichment: Boolean
    )

    private data class ImportedSheetImage(
        val bytes: ByteArray,
        val sourceName: String?
    )

    suspend fun exportToUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = XSSFWorkbook()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersionName = packageInfo.versionName ?: "-"
            val appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString()

            val storageLocations = database.withTransaction { storageLocationDao.getAll() }
            val recentLocationColors = userPreferencesRepository.preferences
                .first()
                .recentLocationColors
            val inventoryItems = database.withTransaction { inventoryItemDao.getAll() }
            val referencedComponentIds = inventoryItems
                .asSequence()
                .map(InventoryItemEntity::componentId)
                .toSet()
            val initialComponents = database.withTransaction {
                componentDao.getAll().filter { it.id in referencedComponentIds }
            }
            val missingDetails = initialComponents
                .filter(::requiresExportEnrichment)
                .map(ComponentEntity::partNumber)
            if (missingDetails.isNotEmpty()) {
                componentEnrichmentManager.enrichNow(missingDetails)
            }
            val components = database.withTransaction {
                componentDao.getAll().filter { it.id in referencedComponentIds }
            }

            workbook.createSheet("meta").apply {
                writeRow(
                    0,
                    listOf("schemaVersion", "1")
                )
                writeRow(
                    1,
                    listOf("appVersionName", appVersionName)
                )
                writeRow(
                    2,
                    listOf("appVersionCode", appVersionCode)
                )
                writeRow(
                    3,
                    listOf("recentLocationColors", recentLocationColors.joinToString("\n"))
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
                val imageColumnIndex = 11
                writeRow(
                    0,
                    listOf(
                        "id",
                        "partNumber",
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

                val metaSheet = wb.getSheet("meta")
                val storageLocations = wb.getSheet("storage_locations").toStorageLocations()
                val recentLocationColors = metaSheet.toRecentLocationColors()
                val componentSheet = wb.getSheet("components")
                val importedComponents = componentSheet.toComponents(componentSheet.extractPreviewImagesByRow())
                val components = importedComponents.map { it.entity }
                val inventoryItems = wb.getSheet("inventory_items").toInventoryItems()

                database.withTransaction {
                    stockOperationDao.deleteAll()
                    stockItemDao.deleteAll()
                    containerDao.deleteContainersByType(ContainerType.LEGACY_LOCATION.name)
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
                    rebuildLegacyContainerStock(storageLocations, inventoryItems)
                    refreshAllLocationCategoryProfilesInternal()
                }
                if (recentLocationColors.isNotEmpty()) {
                    userPreferencesRepository.setRecentLocationColors(recentLocationColors)
                }
                componentEnrichmentManager.scheduleAll(
                    importedComponents
                        .filter { it.requiresEnrichment }
                        .map { it.entity.partNumber }
                )
                null
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "importFromUri failed", throwable)
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

    private suspend fun refreshAllLocationCategoryProfilesInternal() {
        val profilesByLocation = inventoryItemDao.getAllLocationCategoryProfiles()
            .map { projection ->
                LocationCategoryProfile(
                    locationId = projection.locationId,
                    category = projection.category,
                    packageName = projection.packageName,
                    quantity = projection.quantity
                )
            }
            .groupBy(LocationCategoryProfile::locationId)
        storageLocationDao.getAll().forEach { location ->
            val profile = calculateDominantLocationCategoryProfile(profilesByLocation[location.id].orEmpty())
            storageLocationDao.updateInboundProfile(
                locationId = location.id,
                category = profile.category,
                packageName = profile.packageName,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun rebuildLegacyContainerStock(
        locations: List<StorageLocationEntity>,
        inventoryItems: List<InventoryItemEntity>
    ) {
        val slotsByLocationId = mutableMapOf<Long, ContainerSlotEntity>()
        locations.forEach { location ->
            containerDao.insertContainer(
                ContainerEntity(
                    id = location.id,
                    code = location.code,
                    displayName = location.displayName,
                    type = ContainerType.LEGACY_LOCATION.name,
                    slotCount = 1,
                    colorHex = location.colorHex,
                    sortMode = location.sortMode,
                    remark = location.remark,
                    createdAt = location.createdAt,
                    updatedAt = location.createdAt
                )
            )
            val slot = ContainerSlotEntity(
                containerId = location.id,
                slotNumber = 1,
                slotCode = location.code,
                displayName = location.displayName ?: location.code,
                sortOrder = 1,
                createdAt = location.createdAt,
                updatedAt = location.createdAt
            )
            val slotId = containerDao.insertSlot(slot)
            slotsByLocationId[location.id] =
                containerDao.findSlotByContainerAndNumber(location.id, 1) ?: slot.copy(id = slotId)
        }
        inventoryItems.forEach { item ->
            val slot = slotsByLocationId[item.locationId] ?: return@forEach
            stockItemDao.insert(
                StockItemEntity(
                    componentId = item.componentId,
                    containerId = item.locationId,
                    containerSlotId = slot.id,
                    quantity = item.quantity,
                    quantityState = QuantityState.KNOWN.name,
                    lastInboundAt = item.lastInboundAt,
                    updatedAt = item.updatedAt
                )
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
                    protocolPartId = protocolPartIdForImportedComponent(
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

    private fun protocolPartIdForImportedComponent(
        componentId: Long,
        partNumber: String
    ): String? {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        val isManual = normalizedPartNumber.matches(MANUAL_INBOUND_PART_NUMBER_REGEX)
        if (!isManual && normalizedPartNumber.matches(PROTOCOL_PART_ID_REGEX)) {
            return normalizedPartNumber
        }
        return componentId.takeIf { it > 0 }?.let { id -> "M%09d".format(Locale.ROOT, id) }
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
