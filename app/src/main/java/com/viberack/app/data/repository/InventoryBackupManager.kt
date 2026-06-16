package com.viberack.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.viberack.app.R
import com.viberack.app.core.database.dao.BoxDao
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.ContainerDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.dao.InventoryTransactionDao
import com.viberack.app.core.database.dao.StockItemDao
import com.viberack.app.core.database.dao.StockOperationDao
import com.viberack.app.core.database.dao.StorageLocationDao
import com.viberack.app.core.database.entity.BoxEntity
import com.viberack.app.core.database.entity.BoxLayerEntity
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.core.database.entity.ContainerEntity
import com.viberack.app.core.database.entity.ContainerSlotEntity
import com.viberack.app.core.database.entity.InventoryItemEntity
import com.viberack.app.core.database.entity.StockItemEntity
import com.viberack.app.core.database.entity.StockOperationEntity
import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.StorageLocationSortMode
import com.viberack.app.domain.model.calculateDominantLocationCategoryProfile
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
    private val boxDao: BoxDao,
    private val storageLocationDao: StorageLocationDao,
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val containerDao: ContainerDao,
    private val stockItemDao: StockItemDao,
    private val stockOperationDao: StockOperationDao,
    private val componentEnrichmentManager: ComponentEnrichmentManager,
    private val componentImageStore: ComponentImageStore,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) {
    private val exporter = InventoryBackupExporter(
        context = context,
        database = database,
        boxDao = boxDao,
        storageLocationDao = storageLocationDao,
        componentDao = componentDao,
        inventoryItemDao = inventoryItemDao,
        containerDao = containerDao,
        stockItemDao = stockItemDao,
        stockOperationDao = stockOperationDao,
        componentEnrichmentManager = componentEnrichmentManager,
        userPreferencesRepository = userPreferencesRepository
    )
    private val parser = InventoryBackupParser(
        componentImageStore = componentImageStore,
        protocolPartIdStrategy = protocolPartIdStrategy
    )
    private val restorer = InventoryBackupRestorer(
        database = database,
        boxDao = boxDao,
        storageLocationDao = storageLocationDao,
        componentDao = componentDao,
        inventoryItemDao = inventoryItemDao,
        inventoryTransactionDao = inventoryTransactionDao,
        containerDao = containerDao,
        stockItemDao = stockItemDao,
        stockOperationDao = stockOperationDao
    )

    private companion object {
        const val TAG = "InventoryBackupManager"
    }



    suspend fun exportToUri(uri: Uri): String? = exporter.exportToUri(uri)

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                WorkbookFactory.create(inputStream)
            } ?: throw IOException(context.getString(R.string.settings_backup_open_import_failed))

            workbook.use { wb ->
                val schemaVersion = parser.schemaVersion(wb)
                    ?: return@use context.getString(R.string.settings_backup_unsupported_version)
                if (schemaVersion !in 1..2) {
                    return@use context.getString(R.string.settings_backup_unsupported_version)
                }

                val parsedBackup = parser.parse(wb)
                val recentLocationColors = parsedBackup.recentLocationColors
                val importedComponents = parsedBackup.importedComponents
                restorer.restore(parsedBackup)
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




}
