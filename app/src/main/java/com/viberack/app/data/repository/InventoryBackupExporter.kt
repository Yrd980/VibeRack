package com.viberack.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.viberack.app.R
import com.viberack.app.core.database.dao.BoxDao
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.dao.StorageLocationDao
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity
import com.viberack.app.core.datastore.UserPreferencesRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject

internal class InventoryBackupExporter(
    private val context: Context,
    private val database: RoomDatabase,
    private val boxDao: BoxDao,
    private val storageLocationDao: StorageLocationDao,
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val containerDao: ContainerDao,
    private val stockItemDao: StockItemDao,
    private val stockOperationDao: StockOperationDao,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun exportToUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = XSSFWorkbook()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersionName = packageInfo.versionName ?: "-"
            val appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString()

            val storageLocations = database.withTransaction { storageLocationDao.getAll() }
            val boxes = database.withTransaction { boxDao.getAllBoxes() }
            val boxLayers = database.withTransaction { boxDao.getAllLayerEntities() }
            val containers = database.withTransaction { containerDao.getAllContainers() }
            val containerSlots = database.withTransaction { containerDao.getAllSlots() }
            val stockItems = database.withTransaction { stockItemDao.getAll() }
            val stockOperations = database.withTransaction { stockOperationDao.getAll() }
            val recentLocationColors = userPreferencesRepository.preferences
                .first()
                .recentLocationColors
            val inventoryItems = database.withTransaction { inventoryItemDao.getAll() }
            val referencedComponentIds = (inventoryItems.asSequence().map(InventoryItemEntity::componentId) +
                stockItems.asSequence().map(StockItemEntity::componentId) +
                stockOperations.asSequence().mapNotNull(StockOperationEntity::componentId))
                .asSequence()
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
                    listOf("schemaVersion", "2")
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

            workbook.createSheet("boxes").apply {
                writeRow(0, listOf("id", "code", "name", "layerCount", "createdAt", "updatedAt"))
                boxes.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.code,
                            item.name,
                            item.layerCount,
                            item.createdAt,
                            item.updatedAt
                        )
                    )
                }
            }

            workbook.createSheet("box_layers").apply {
                writeRow(0, listOf("id", "boxId", "layerCode", "displayName", "sortOrder", "createdAt", "updatedAt"))
                boxLayers.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.boxId,
                            item.layerCode,
                            item.displayName,
                            item.sortOrder,
                            item.createdAt,
                            item.updatedAt
                        )
                    )
                }
            }

            workbook.createSheet("containers").apply {
                writeRow(
                    0,
                    listOf(
                        "id",
                        "code",
                        "displayName",
                        "type",
                        "slotCount",
                        "colorHex",
                        "sortMode",
                        "remark",
                        "createdAt",
                        "updatedAt",
                        "macAddress",
                        "batchId",
                        "protoVersion",
                        "firmwareVersion",
                        "hardwareVersion",
                        "batteryPct",
                        "statusFlags",
                        "tableSeq",
                        "tableCrc16",
                        "lastSeenAt",
                        "lastSyncedAt"
                    )
                )
                containers.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.code,
                            item.displayName,
                            item.type,
                            item.slotCount,
                            item.colorHex,
                            item.sortMode,
                            item.remark,
                            item.createdAt,
                            item.updatedAt,
                            item.macAddress,
                            item.batchId,
                            item.protoVersion,
                            item.firmwareVersion,
                            item.hardwareVersion,
                            item.batteryPct,
                            item.statusFlags,
                            item.tableSeq,
                            item.tableCrc16,
                            item.lastSeenAt,
                            item.lastSyncedAt
                        )
                    )
                }
            }

            workbook.createSheet("container_slots").apply {
                writeRow(
                    0,
                    listOf("id", "containerId", "slotNumber", "slotCode", "displayName", "sortOrder", "createdAt", "updatedAt")
                )
                containerSlots.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.containerId,
                            item.slotNumber,
                            item.slotCode,
                            item.displayName,
                            item.sortOrder,
                            item.createdAt,
                            item.updatedAt
                        )
                    )
                }
            }

            workbook.createSheet("stock_items").apply {
                writeRow(
                    0,
                    listOf(
                        "id",
                        "componentId",
                        "containerId",
                        "containerSlotId",
                        "quantity",
                        "quantityState",
                        "safetyStockThreshold",
                        "lastInboundAt",
                        "updatedAt"
                    )
                )
                stockItems.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.componentId,
                            item.containerId,
                            item.containerSlotId,
                            item.quantity,
                            item.quantityState,
                            item.safetyStockThreshold,
                            item.lastInboundAt,
                            item.updatedAt
                        )
                    )
                }
            }

            workbook.createSheet("stock_operations").apply {
                writeRow(
                    0,
                    listOf(
                        "id",
                        "type",
                        "containerId",
                        "containerSlotId",
                        "componentId",
                        "quantityDelta",
                        "sourceType",
                        "sourceRef",
                        "rawPayload",
                        "bleOpcode",
                        "bleStatus",
                        "tableSeqBefore",
                        "tableSeqAfter",
                        "createdAt"
                    )
                )
                stockOperations.forEachIndexed { index, item ->
                    writeRow(
                        index + 1,
                        listOf(
                            item.id,
                            item.type,
                            item.containerId,
                            item.containerSlotId,
                            item.componentId,
                            item.quantityDelta,
                            item.sourceType,
                            item.sourceRef,
                            item.rawPayload,
                            item.bleOpcode,
                            item.bleStatus,
                            item.tableSeqBefore,
                            item.tableSeqAfter,
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

}
