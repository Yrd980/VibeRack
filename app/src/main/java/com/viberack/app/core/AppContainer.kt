package com.viberack.app.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.withTransaction
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.viberack.app.core.ble.smart.SmartChassisGattClient
import com.viberack.app.core.ble.smart.SmartChassisManager
import com.viberack.app.core.ble.smart.SmartChassisOperations
import com.viberack.app.core.ble.smart.SmartChassisScanner
import com.viberack.app.core.database.AppDatabase
import com.viberack.app.core.datastore.UserPreferencesRepository
import com.viberack.app.core.nfc.NfcLabelManager
import com.viberack.app.core.printer.P0PrinterManager
import com.viberack.app.core.printer.PrinterManager
import com.viberack.app.core.printer.Q5PrinterManager
import com.viberack.app.data.repository.ComponentEnrichmentManager
import com.viberack.app.data.repository.BoxRepositoryImpl
import com.viberack.app.data.remote.LcscCatalogRemoteDataSource
import com.viberack.app.data.repository.ComponentImageStore
import com.viberack.app.data.repository.ContainerRepositoryImpl
import com.viberack.app.data.repository.InventoryBackupManager
import com.viberack.app.data.repository.InventoryRepositoryImpl
import com.viberack.app.data.repository.LcscCatalogRepositoryImpl
import com.viberack.app.data.repository.ProtocolPartIdStrategy
import com.viberack.app.data.repository.SlotOperationRepositoryImpl
import com.viberack.app.data.repository.StockPlacementRepositoryImpl
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.calculateDominantLocationCategoryProfile
import com.viberack.app.domain.repository.BoxRepository
import com.viberack.app.domain.repository.ContainerRepository
import com.viberack.app.domain.repository.InventoryRepository
import com.viberack.app.domain.repository.LcscCatalogRepository
import com.viberack.app.domain.repository.SlotOperationRepository
import com.viberack.app.domain.repository.StockPlacementRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.viberack.app.core.database.DatabaseMigrations

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "viberack.db"
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
    private val protocolPartIdStrategy = ProtocolPartIdStrategy()
    private val stockPlacementRepository: StockPlacementRepository = StockPlacementRepositoryImpl(
        containerDao = database.containerDao(),
        stockItemDao = database.stockItemDao(),
        stockOperationDao = database.stockOperationDao()
    )

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
                val inventoryItemDao = database.inventoryItemDao()
                val locationIds = inventoryItemDao
                    .getLegacyLocationIdsByComponentFromStock(componentId)
                    .ifEmpty { inventoryItemDao.getLocationIdsByComponent(componentId) }
                locationIds
                    .forEach { locationId ->
                        val profile = calculateDominantLocationCategoryProfile(
                            inventoryItemDao
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
        containerDao = database.containerDao(),
        stockPlacementRepository = stockPlacementRepository,
        componentEnrichmentManager = componentEnrichmentManager,
        componentImageStore = componentImageStore,
        protocolPartIdStrategy = protocolPartIdStrategy
    )

    val boxRepository: BoxRepository = BoxRepositoryImpl(
        database = database,
        boxDao = database.boxDao(),
        componentDao = database.componentDao(),
        containerDao = database.containerDao(),
        stockPlacementRepository = stockPlacementRepository,
        protocolPartIdStrategy = protocolPartIdStrategy
    )

    val containerRepository: ContainerRepository = ContainerRepositoryImpl(
        database = database,
        containerDao = database.containerDao(),
        componentDao = database.componentDao(),
        stockPlacementRepository = stockPlacementRepository,
        protocolPartIdStrategy = protocolPartIdStrategy
    )

    val smartChassisManager = SmartChassisManager(
        client = SmartChassisGattClient(
            appContext = appContext,
            hasBluetoothPermission = ::hasSmartChassisBluetoothPermission
        )
    )
    val smartChassisOperations = SmartChassisOperations(
        manager = smartChassisManager,
        containerRepository = containerRepository,
        protocolPartIdStrategy = protocolPartIdStrategy
    )
    val smartChassisScanner = SmartChassisScanner(appContext)

    val slotOperationRepository: SlotOperationRepository = SlotOperationRepositoryImpl(
        database = database,
        containerDao = database.containerDao(),
        componentDao = database.componentDao(),
        stockPlacementRepository = stockPlacementRepository,
        smartChassisOperations = smartChassisOperations,
        protocolPartIdStrategy = protocolPartIdStrategy
    )

    val inventoryBackupManager = InventoryBackupManager(
        context = appContext,
        database = database,
        boxDao = database.boxDao(),
        storageLocationDao = database.storageLocationDao(),
        componentDao = database.componentDao(),
        inventoryItemDao = database.inventoryItemDao(),
        inventoryTransactionDao = database.inventoryTransactionDao(),
        containerDao = database.containerDao(),
        stockItemDao = database.stockItemDao(),
        stockOperationDao = database.stockOperationDao(),
        componentEnrichmentManager = componentEnrichmentManager,
        componentImageStore = componentImageStore,
        userPreferencesRepository = userPreferencesRepository,
        protocolPartIdStrategy = protocolPartIdStrategy
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

    private fun hasSmartChassisBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
