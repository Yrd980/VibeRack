package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.StockContainer

data class SlotOperationWrite(
    val containerId: Long,
    val slotNumber: Int,
    val component: ComponentDetail,
    val quantity: Int,
    val sourceType: String,
    val rawPayload: String? = null
)

data class SlotOperationResult(
    val success: Boolean,
    val message: String? = null,
    val affectedSlots: List<ContainerSlotStock> = emptyList()
)

interface SlotOperationRepository {
    suspend fun writeOne(write: SlotOperationWrite): SlotOperationResult
    suspend fun clearOne(containerId: Long, slotNumber: Int): SlotOperationResult
    suspend fun insertAt(write: SlotOperationWrite): SlotOperationResult
    suspend fun removeAt(containerId: Long, slotNumber: Int): SlotOperationResult
    suspend fun moveBlock(containerId: Long, fromSlotNumber: Int, toSlotNumber: Int, length: Int): SlotOperationResult
    suspend fun setQuantity(containerId: Long, slotNumber: Int, quantity: Int): SlotOperationResult
    suspend fun resolveLocalComponent(partIdOrNumber: String): ComponentDetail?
    suspend fun findContainer(containerId: Long): StockContainer?
}
