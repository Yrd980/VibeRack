package com.viberack.app.domain.repository

import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ContainerSlot
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.SmartChassisSlotRecord
import com.viberack.app.domain.model.SmartChassisTableInfo
import com.viberack.app.domain.model.SlotStockItem
import com.viberack.app.domain.model.StockContainer
import kotlinx.coroutines.flow.Flow

interface ContainerRepository {
    fun observeContainers(): Flow<List<StockContainer>>
    fun observeContainerSlotStock(containerId: Long): Flow<List<ContainerSlotStock>>
    fun observeSlotStock(containerId: Long): Flow<List<SlotStockItem>>
    suspend fun findContainer(containerId: Long): StockContainer?
    suspend fun findContainerByMacAddress(macAddress: String): StockContainer?
    suspend fun ensureSmartChassisContainer(
        macAddress: String,
        batchId: Int,
        protoVersion: Int,
        batteryPct: Int? = null,
        statusFlags: Int? = null,
        tableSeqLow16: Int? = null,
        advertisedName: String? = null
    ): StockContainer?
    suspend fun getSlots(containerId: Long): List<ContainerSlot>
    suspend fun restoreSmartChassisTable(
        containerId: Long,
        records: List<SmartChassisSlotRecord>,
        tableInfo: SmartChassisTableInfo
    ): Int
    suspend fun findComponentByProtocolPartId(protocolPartId: String): ComponentDetail?
    suspend fun findOrCreateManualPlaceholder(protocolPartId: String): ComponentDetail
}
