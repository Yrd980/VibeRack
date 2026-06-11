package com.example.lcsc_android_erp.core.ble.smart

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartChassisManager(
    private val client: SmartChassisClient
) {
    val discoveredChassis: StateFlow<List<SmartChassisDevice>> = client.discoveredChassis
    val connectionState: StateFlow<SmartChassisConnectionState> = client.connectionState
    val activeTableInfo: StateFlow<SmartChassisTableInfo?> = client.tableInfoUpdates

    private val _lastOperationError = MutableStateFlow<SmartChassisOperationError?>(null)
    val lastOperationError: StateFlow<SmartChassisOperationError?> = _lastOperationError.asStateFlow()

    suspend fun startScan(): List<SmartChassisDevice> {
        return when (val result = client.startScan()) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                emptyList()
            }
        }
    }

    suspend fun stopScan() {
        when (val result = client.stopScan()) {
            is SmartChassisClientResult.Success -> clearError()
            is SmartChassisClientResult.Failure -> recordError(result)
        }
    }

    suspend fun connect(address: String): SmartChassisDevice? {
        return when (val result = client.connect(address)) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    suspend fun disconnect() {
        when (val result = client.disconnect()) {
            is SmartChassisClientResult.Success -> {
                clearError()
            }
            is SmartChassisClientResult.Failure -> recordError(result)
        }
    }

    suspend fun refreshTableInfo(): SmartChassisTableInfo? {
        return when (val result = client.readTableInfo()) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    suspend fun readOne(slot: Int): SmartChassisSlotRecord? {
        return when (val result = client.readOne(slot)) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    suspend fun readAll(): SmartChassisTableSnapshot? {
        return when (val result = client.readAll()) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    suspend fun writeOne(record: SmartChassisSlotRecord): SmartChassisTableInfo? {
        return updateTableInfo(client.writeOne(record))
    }

    suspend fun clearOne(slot: Int): SmartChassisTableInfo? {
        return updateTableInfo(client.clearOne(slot))
    }

    suspend fun insertAt(slot: Int, record: SmartChassisSlotRecord): SmartChassisTableInfo? {
        return updateTableInfo(client.insertAt(slot, record))
    }

    suspend fun removeAt(slot: Int): SmartChassisTableInfo? {
        return updateTableInfo(client.removeAt(slot))
    }

    suspend fun moveBlock(from: Int, to: Int, length: Int): SmartChassisTableInfo? {
        return updateTableInfo(client.moveBlock(from, to, length))
    }

    suspend fun setQuantity(slot: Int, quantity: Int): SmartChassisTableInfo? {
        return updateTableInfo(client.setQuantity(slot, quantity))
    }

    suspend fun sendLightCommand(command: SmartChassisLightCommand): SmartChassisLightStatus? {
        return when (val result = client.sendLightCommand(command)) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    private fun updateTableInfo(
        result: SmartChassisClientResult<SmartChassisTableInfo>
    ): SmartChassisTableInfo? {
        return when (result) {
            is SmartChassisClientResult.Success -> {
                clearError()
                result.value
            }
            is SmartChassisClientResult.Failure -> {
                recordError(result)
                null
            }
        }
    }

    private fun clearError() {
        _lastOperationError.value = null
    }

    private fun recordError(result: SmartChassisClientResult.Failure) {
        _lastOperationError.value = SmartChassisOperationError(
            op = result.op,
            status = result.status,
            message = result.message
        )
    }
}
