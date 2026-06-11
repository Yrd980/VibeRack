package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.QuantityState
import com.example.lcsc_android_erp.domain.model.SlotStockItem
import com.example.lcsc_android_erp.domain.model.StockOperation
import kotlinx.coroutines.flow.Flow

data class StockPlacementWrite(
    val containerId: Long,
    val slotId: Long,
    val componentId: Long,
    val quantity: Int,
    val quantityState: QuantityState = QuantityState.KNOWN,
    val safetyStockThreshold: Int? = null,
    val lastInboundAt: Long,
    val updatedAt: Long
)

interface StockPlacementRepository {
    fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>>
    fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>>
    suspend fun upsertStock(write: StockPlacementWrite): Long?
    suspend fun replaceSlotStock(write: StockPlacementWrite): Long?
    suspend fun deleteSlotStock(slotId: Long)
    suspend fun deleteComponentFromSlot(componentId: Long, slotId: Long)
    suspend fun deleteContainerStock(containerId: Long)
    suspend fun recordOperation(operation: StockOperation): Long
}
