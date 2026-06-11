package com.example.lcsc_android_erp.domain.repository

import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ContainerSlot
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.SlotStockItem
import com.example.lcsc_android_erp.domain.model.StockContainer
import kotlinx.coroutines.flow.Flow

interface ContainerRepository {
    fun observeContainers(): Flow<List<StockContainer>>
    fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>>
    fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>>
    suspend fun findContainer(containerId: Long): StockContainer?
    suspend fun findContainerByMacAddress(macAddress: String): StockContainer?
    suspend fun getSlots(containerId: Long): List<ContainerSlot>
    suspend fun findComponentByProtocolPartId(protocolPartId: String): ComponentDetail?
    suspend fun findOrCreateManualPlaceholder(protocolPartId: String): ComponentDetail
}
