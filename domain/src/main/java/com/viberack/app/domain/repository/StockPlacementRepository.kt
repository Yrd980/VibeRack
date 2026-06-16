package com.viberack.app.domain.repository

import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.QuantityState
import com.viberack.app.domain.model.SlotStockItem
import com.viberack.app.domain.model.StockOperation
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
    suspend fun getContainerSlotStock(containerId: Long): List<ContainerSlotStock>
    suspend fun findSlotStock(slotId: Long): ContainerSlotStock?
    suspend fun upsertStock(write: StockPlacementWrite): Long?
    suspend fun replaceSlotStock(write: StockPlacementWrite): Long?
    suspend fun updateStockQuantity(stockItemId: Long, quantity: Int, updatedAt: Long)
    suspend fun deleteSlotStock(slotId: Long)
    suspend fun deleteComponentFromSlot(componentId: Long, slotId: Long)
    suspend fun deleteContainerStock(containerId: Long)
    suspend fun recordOperation(operation: StockOperation): Long
}
