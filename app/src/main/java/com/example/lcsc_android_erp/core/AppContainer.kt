package com.example.lcsc_android_erp.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.withTransaction
import com.example.lcsc_android_erp.core.database.AppDatabase
import com.example.lcsc_android_erp.core.datastore.UserPreferencesRepository
import com.example.lcsc_android_erp.core.nfc.NfcLabelManager
import com.example.lcsc_android_erp.core.printer.P0PrinterManager
import com.example.lcsc_android_erp.core.printer.PrinterManager
import com.example.lcsc_android_erp.core.printer.Q5PrinterManager
import com.example.lcsc_android_erp.data.repository.ComponentEnrichmentManager
import com.example.lcsc_android_erp.data.repository.BoxRepositoryImpl
import com.example.lcsc_android_erp.data.remote.LcscCatalogRemoteDataSource
import com.example.lcsc_android_erp.data.repository.ComponentImageStore
import com.example.lcsc_android_erp.data.repository.InventoryBackupManager
import com.example.lcsc_android_erp.data.repository.InventoryRepositoryImpl
import com.example.lcsc_android_erp.data.repository.LcscCatalogRepositoryImpl
import com.example.lcsc_android_erp.domain.model.LocationCategoryProfile
import com.example.lcsc_android_erp.domain.model.calculateDominantLocationCategoryProfile
import com.example.lcsc_android_erp.domain.repository.BoxRepository
import com.example.lcsc_android_erp.domain.repository.InventoryRepository
import com.example.lcsc_android_erp.domain.repository.LcscCatalogRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.lcsc_android_erp.core.database.DatabaseMigrations

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "lcsc_erp.db"
    ).addMigrations(*DatabaseMigrations.ALL).build()

    private val preferencesDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { File(appContext.filesDir, "settings.preferences_pb") }
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val userPreferencesRepository = UserPreferencesRepository(preferencesDataStore)
    val nfcLabelManager = NfcLabelManager(appContext)

    private val componentImageStore = ComponentImageStore(
        context = appContext,
        okHttpClient = okHttpClient
    )

    val componentEnrichmentManager = ComponentEnrichmentManager(
        componentDao = database.componentDao(),
        lcscCatalogRepository = LcscCatalogRepositoryImpl(
            remoteDataSource = LcscCatalogRemoteDataSource(okHttpClient)
        ),
        componentImageStore = componentImageStore,
        onComponentEnriched = { componentId ->
            database.withTransaction {
                database.inventoryItemDao()
                    .getLocationIdsByComponent(componentId)
                    .forEach { locationId ->
                        val profile = calculateDominantLocationCategoryProfile(
                            database.inventoryItemDao()
                                .getLocationCategoryProfiles(locationId)
                                .map { projection ->
                                    LocationCategoryProfile(
                                        locationId = projection.locationId,
                                        category = projection.category,
                                        packageName = projection.packageName,
                                        quantity = projection.quantity
                                    )
                                }
                        )
                        database.storageLocationDao().updateInboundProfile(
                            locationId = locationId,
                            category = profile.category,
                            packageName = profile.packageName,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
            }
        }
    )

    val inventoryRepository: InventoryRepository = InventoryRepositoryImpl(
        context = appContext,
        database = database,
        componentDao = database.componentDao(),
        dashboardDao = database.dashboardDao(),
        storageLocationDao = database.storageLocationDao(),
        inventoryItemDao = database.inventoryItemDao(),
        inventoryTransactionDao = database.inventoryTransactionDao(),
        componentEnrichmentManager = componentEnrichmentManager,
        componentImageStore = componentImageStore
    )

    val boxRepository: BoxRepository = BoxRepositoryImpl(
        database = database,
        boxDao = database.boxDao(),
        componentDao = database.componentDao()
    )

    val inventoryBackupManager = InventoryBackupManager(
        context = appContext,
        database = database,
        storageLocationDao = database.storageLocationDao(),
        componentDao = database.componentDao(),
        inventoryItemDao = database.inventoryItemDao(),
        inventoryTransactionDao = database.inventoryTransactionDao(),
        componentEnrichmentManager = componentEnrichmentManager,
        componentImageStore = componentImageStore,
        userPreferencesRepository = userPreferencesRepository
    )

    val lcscCatalogRepository: LcscCatalogRepository = LcscCatalogRepositoryImpl(
        remoteDataSource = LcscCatalogRemoteDataSource(okHttpClient)
    )

    val q5PrinterManager = Q5PrinterManager(appContext)
    val p0PrinterManager = P0PrinterManager(appContext)

    fun printerManagerForType(printerType: String): PrinterManager {
        return when (printerType) {
            UserPreferencesRepository.PRINTER_TYPE_YINLIFANG_P0 -> p0PrinterManager
            else -> q5PrinterManager
        }
    }
}
