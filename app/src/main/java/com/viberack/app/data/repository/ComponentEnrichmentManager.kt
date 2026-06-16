package com.viberack.app.data.repository

import android.util.Log
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.domain.repository.LcscCatalogRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ComponentEnrichmentState(
    val isRunning: Boolean = false,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val activeCount: Int = 0
) {
    val progress: Float
        get() = if (totalCount <= 0) 0f else completedCount.toFloat() / totalCount.toFloat()
}

class ComponentEnrichmentManager(
    private val componentDao: ComponentDao,
    private val lcscCatalogRepository: LcscCatalogRepository,
    private val componentImageStore: ComponentImageStore,
    private val onComponentEnriched: suspend (Long) -> Unit = {}
) {
    private companion object {
        private const val TAG = "ComponentEnrichment"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val runningPartNumbers = ConcurrentHashMap.newKeySet<String>()
    private val enrichMutex = Mutex()
    private val _state = MutableStateFlow(ComponentEnrichmentState())
    val state: StateFlow<ComponentEnrichmentState> = _state.asStateFlow()

    fun schedule(partNumber: String) {
        val normalizedPartNumber = partNumber.trim().uppercase()
        if (normalizedPartNumber.isBlank()) {
            return
        }
        if (!runningPartNumbers.add(normalizedPartNumber)) {
            return
        }
        _state.update { current ->
            val resetSession = !current.isRunning
            val base = if (resetSession) {
                ComponentEnrichmentState(isRunning = true)
            } else {
                current
            }
            base.copy(
                isRunning = true,
                totalCount = base.totalCount + 1,
                activeCount = base.activeCount + 1
            )
        }
        scope.launch {
            try {
                enrichMutex.withLock {
                    runCatching {
                        enrich(normalizedPartNumber)
                    }.onFailure { throwable ->
                        Log.w(
                            TAG,
                            "enrich failed partNumber=$normalizedPartNumber",
                            throwable
                        )
                    }
                    delay(1_000)
                }
            } finally {
                runningPartNumbers.remove(normalizedPartNumber)
                _state.update { current ->
                    val nextActiveCount = (current.activeCount - 1).coerceAtLeast(0)
                    val nextCompletedCount = (current.completedCount + 1).coerceAtMost(current.totalCount)
                    if (nextActiveCount == 0) {
                        current.copy(
                            isRunning = false,
                            activeCount = 0,
                            completedCount = nextCompletedCount
                        )
                    } else {
                        current.copy(
                            isRunning = true,
                            activeCount = nextActiveCount,
                            completedCount = nextCompletedCount
                        )
                    }
                }
            }
        }
    }

    fun scheduleAll(partNumbers: Iterable<String>) {
        partNumbers.forEach(::schedule)
    }

    suspend fun enrichNow(partNumbers: Iterable<String>) {
        partNumbers
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { partNumber ->
                enrichMutex.withLock {
                    runCatching {
                        enrich(partNumber)
                    }.onFailure { throwable ->
                        Log.w(TAG, "enrichNow failed partNumber=$partNumber", throwable)
                    }
                    delay(1_000)
                }
            }
    }

    private suspend fun enrich(partNumber: String) {
        val existing = componentDao.findByPartNumber(partNumber) ?: return
        val detail = lcscCatalogRepository.lookupByPartNumber(partNumber) ?: return
        val localImagePath = componentImageStore.persistImage(
            partNumber = partNumber,
            imageUrl = detail.imageUrl
        )
        val updated = existing.copy(
            mpn = detail.mpn,
            name = detail.name,
            brand = detail.brand,
            packageName = detail.packageName,
            category = detail.category,
            specJson = detail.specifications
                .takeIf { it.isNotEmpty() }
                ?.let { JSONObject(it).toString() },
            description = detail.description,
            sourceUrl = detail.productUrl,
            imageLocalPath = localImagePath ?: existing.imageLocalPath,
            updatedAt = System.currentTimeMillis()
        )
        componentDao.update(updated)
        onComponentEnriched(existing.id)
    }
}
