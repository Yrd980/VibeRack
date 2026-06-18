package com.viberack.app.core.ble.smart

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSmartChassisClient(
    private val address: String = "FA:KE:00:00:00:01",
    private val name: String = "VBRK-0001",
    private val batchId: Int = 1,
    initialRecords: List<SmartChassisSlotRecord> = emptyList()
) : SmartChassisClient {
    private val records = MutableList(SmartChassisProtocol.SLOT_COUNT) { emptyRecord() }
    private var tableSeq: Long = 1
    private var activeLightStatus = SmartChassisLightStatus(
        mode = SmartChassisLightMode.OFF,
        rawMode = SmartChassisLightMode.OFF.code,
        remainingSeconds = 0
    )
    private var nextFlashBusyOp: SmartChassisBindingOp? = null

    private val fakeDevice: SmartChassisDevice
        get() = SmartChassisDevice(
            address = address,
            name = name,
            rssi = -48,
            advertisement = SmartChassisAdvertisement(
                companyId = SmartChassisProtocol.DEV_COMPANY_ID,
                protoVersion = SmartChassisProtocol.PROTOCOL_VERSION,
                batchId = batchId,
                batteryPct = 100,
                statusFlags = advertisementFlags(),
                tableSeqLow16 = (tableSeq and 0xFFFF).toInt()
            )
        )

    private val _discoveredChassis = MutableStateFlow<List<SmartChassisDevice>>(emptyList())
    override val discoveredChassis: StateFlow<List<SmartChassisDevice>> = _discoveredChassis.asStateFlow()

    private val _connectionState = MutableStateFlow(SmartChassisConnectionState())
    override val connectionState: StateFlow<SmartChassisConnectionState> = _connectionState.asStateFlow()

    private val _tableInfoUpdates = MutableStateFlow<SmartChassisTableInfo?>(null)
    override val tableInfoUpdates: StateFlow<SmartChassisTableInfo?> = _tableInfoUpdates.asStateFlow()

    init {
        initialRecords.forEach { record ->
            if (record.slot in 1..SmartChassisProtocol.SLOT_COUNT && !record.isEmpty) {
                records[record.slot - 1] = normalizedRecord(record, record.slot)
            }
        }
    }

    fun simulateNextFlashBusy(op: SmartChassisBindingOp) {
        nextFlashBusyOp = op
    }

    override suspend fun startScan(): SmartChassisClientResult<List<SmartChassisDevice>> {
        val device = fakeDevice
        _discoveredChassis.value = listOf(device)
        _connectionState.value = SmartChassisConnectionState(
            phase = SmartChassisConnectionPhase.SCANNING,
            message = "Fake scan active"
        )
        return SmartChassisClientResult.Success(listOf(device))
    }

    override suspend fun stopScan(): SmartChassisClientResult<Unit> {
        if (!_connectionState.value.isConnected) {
            _connectionState.value = SmartChassisConnectionState()
        }
        return SmartChassisClientResult.Success(Unit)
    }

    override suspend fun connect(address: String): SmartChassisClientResult<SmartChassisDevice> {
        val device = fakeDevice
        if (!address.equals(device.address, ignoreCase = true)) {
            _connectionState.value = SmartChassisConnectionState(
                phase = SmartChassisConnectionPhase.DISCONNECTED,
                message = "Fake chassis not found: $address"
            )
            return SmartChassisClientResult.Failure("Fake chassis not found: $address")
        }
        _connectionState.value = SmartChassisConnectionState(
            phase = SmartChassisConnectionPhase.CONNECTED,
            device = device
        )
        _discoveredChassis.value = listOf(device)
        return SmartChassisClientResult.Success(device)
    }

    override suspend fun disconnect(): SmartChassisClientResult<Unit> {
        _connectionState.value = SmartChassisConnectionState()
        _tableInfoUpdates.value = null
        return SmartChassisClientResult.Success(Unit)
    }

    override suspend fun readTableInfo(): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedResult {
            tableInfo().also { _tableInfoUpdates.value = it }
        }
    }

    override suspend fun readOne(slot: Int): SmartChassisClientResult<SmartChassisSlotRecord> {
        return connectedBindingResult(SmartChassisBindingOp.READ_ONE) {
            if (!isValidSlot(slot)) {
                return@connectedBindingResult bindingFailure("slot must be 1..25", SmartChassisBindingOp.READ_ONE)
            }
            SmartChassisClientResult.Success(records[slot - 1], SmartChassisBindingOp.READ_ONE)
        }
    }

    override suspend fun readAll(): SmartChassisClientResult<SmartChassisTableSnapshot> {
        return connectedBindingResult(SmartChassisBindingOp.READ_ALL) {
            SmartChassisClientResult.Success(
                SmartChassisTableSnapshot(
                    records = records.toList(),
                    tableInfo = tableInfo()
                ),
                SmartChassisBindingOp.READ_ALL
            )
        }
    }

    override suspend fun readDeviceHealth(): SmartChassisClientResult<SmartChassisDeviceHealth> {
        return connectedResult {
            SmartChassisDeviceHealth(
                batteryPct = 100,
                resetReason = 0x0002,
                healthFlags = 0
            )
        }
    }

    override suspend fun writeOne(record: SmartChassisSlotRecord): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.WRITE_ONE) {
            if (!isValidWritableRecord(record)) {
                return@connectedBindingResult bindingFailure("record must target slot 1..25", SmartChassisBindingOp.WRITE_ONE)
            }
            records[record.slot - 1] = normalizedRecord(record, record.slot)
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.WRITE_ONE)
        }
    }

    override suspend fun clearOne(slot: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.CLEAR_ONE) {
            if (!isValidSlot(slot)) {
                return@connectedBindingResult bindingFailure("slot must be 1..25", SmartChassisBindingOp.CLEAR_ONE)
            }
            records[slot - 1] = emptyRecord()
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.CLEAR_ONE)
        }
    }

    override suspend fun insertAt(
        slot: Int,
        record: SmartChassisSlotRecord
    ): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.INSERT_AT) {
            if (!isValidSlot(slot) || !isValidWritableRecord(record)) {
                return@connectedBindingResult bindingFailure("slot and record must target 1..25", SmartChassisBindingOp.INSERT_AT)
            }
            if (!records.last().isEmpty) {
                return@connectedBindingResult bindingFailure(
                    message = "smart chassis is full",
                    op = SmartChassisBindingOp.INSERT_AT,
                    status = SmartChassisBindingStatus.ERR_FULL
                )
            }
            for (index in records.lastIndex downTo slot) {
                records[index] = records[index - 1].renumber(index + 1)
            }
            records[slot - 1] = normalizedRecord(record, slot)
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.INSERT_AT)
        }
    }

    override suspend fun removeAt(slot: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.REMOVE_AT) {
            if (!isValidSlot(slot)) {
                return@connectedBindingResult bindingFailure("slot must be 1..25", SmartChassisBindingOp.REMOVE_AT)
            }
            for (index in slot - 1 until records.lastIndex) {
                records[index] = records[index + 1].renumber(index + 1)
            }
            records[records.lastIndex] = emptyRecord()
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.REMOVE_AT)
        }
    }

    override suspend fun moveBlock(
        from: Int,
        to: Int,
        length: Int
    ): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.MOVE_BLOCK) {
            if (!isValidBlock(from, to, length)) {
                return@connectedBindingResult bindingFailure("block must stay within 25 slots", SmartChassisBindingOp.MOVE_BLOCK)
            }
            if (from == to) {
                return@connectedBindingResult SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.MOVE_BLOCK)
            }
            val block = records.subList(from - 1, from - 1 + length).map { it }
            val rest = records.filterIndexed { index, _ ->
                val slot = index + 1
                slot < from || slot >= from + length
            }
            val insertAt = (to - 1).coerceAtMost(rest.size)
            val moved = buildList {
                addAll(rest.take(insertAt))
                addAll(block)
                addAll(rest.drop(insertAt))
            }.take(SmartChassisProtocol.SLOT_COUNT)
            for (index in records.indices) {
                records[index] = moved[index].renumber(index + 1)
            }
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.MOVE_BLOCK)
        }
    }

    override suspend fun setQuantity(slot: Int, quantity: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        return connectedBindingResult(SmartChassisBindingOp.SET_QTY) {
            if (!isValidSlot(slot) || quantity !in 0..0xFFFF || records[slot - 1].isEmpty) {
                return@connectedBindingResult bindingFailure(
                    message = "slot must be occupied and quantity must fit uint16",
                    op = SmartChassisBindingOp.SET_QTY
                )
            }
            records[slot - 1] = records[slot - 1].copy(quantity = quantity).renumber(slot)
            commitTable()
            SmartChassisClientResult.Success(tableInfo(), SmartChassisBindingOp.SET_QTY)
        }
    }

    override suspend fun sendLightCommand(
        command: SmartChassisLightCommand
    ): SmartChassisClientResult<SmartChassisLightStatus> {
        if (!_connectionState.value.isConnected) {
            return SmartChassisClientResult.Failure("smart chassis is not connected")
        }
        val encoded = runCatching { SmartChassisCodec.encodeLightCommand(command) }
            .getOrElse { throwable ->
                return SmartChassisClientResult.Failure(throwable.message ?: "invalid light command")
            }
        val mode = encoded[0].toInt() and 0xFF
        val timeout = when {
            command.mode == SmartChassisLightMode.OFF -> 0
            command.timeoutSeconds == 0 -> SmartChassisProtocol.DEFAULT_LIGHT_TIMEOUT_SECONDS
            command.mode == SmartChassisLightMode.FX ->
                command.timeoutSeconds.coerceAtMost(SmartChassisProtocol.MAX_FX_TIMEOUT_SECONDS)
            else -> command.timeoutSeconds.coerceAtMost(SmartChassisProtocol.MAX_LIGHT_TIMEOUT_SECONDS)
        }
        activeLightStatus = SmartChassisLightStatus(
            mode = SmartChassisLightMode.fromCode(mode),
            rawMode = mode,
            remainingSeconds = timeout
        )
        _discoveredChassis.value = listOf(fakeDevice)
        _connectionState.value = _connectionState.value.copy(device = fakeDevice)
        return SmartChassisClientResult.Success(activeLightStatus)
    }

    private inline fun <T> connectedResult(block: () -> T): SmartChassisClientResult<T> {
        return if (_connectionState.value.isConnected) {
            SmartChassisClientResult.Success(block())
        } else {
            SmartChassisClientResult.Failure("smart chassis is not connected")
        }
    }

    private inline fun <T> connectedBindingResult(
        op: SmartChassisBindingOp,
        block: () -> SmartChassisClientResult<T>
    ): SmartChassisClientResult<T> {
        if (!_connectionState.value.isConnected) {
            return SmartChassisClientResult.Failure("smart chassis is not connected", op)
        }
        if (nextFlashBusyOp == op) {
            nextFlashBusyOp = null
            return bindingFailure(
                message = "fake flash busy",
                op = op,
                status = SmartChassisBindingStatus.ERR_FLASH_BUSY
            )
        }
        return block()
    }

    private fun bindingFailure(
        message: String,
        op: SmartChassisBindingOp,
        status: SmartChassisBindingStatus = SmartChassisBindingStatus.ERR_PARAM
    ): SmartChassisClientResult.Failure {
        return SmartChassisClientResult.Failure(message = message, op = op, status = status)
    }

    private fun isValidSlot(slot: Int): Boolean {
        return slot in 1..SmartChassisProtocol.SLOT_COUNT
    }

    private fun isValidWritableRecord(record: SmartChassisSlotRecord): Boolean {
        return record.slot in 1..SmartChassisProtocol.SLOT_COUNT && !record.isEmpty
    }

    private fun isValidBlock(from: Int, to: Int, length: Int): Boolean {
        return isValidSlot(from) &&
            isValidSlot(to) &&
            length in 1..SmartChassisProtocol.SLOT_COUNT &&
            from + length - 1 <= SmartChassisProtocol.SLOT_COUNT &&
            to + length - 1 <= SmartChassisProtocol.SLOT_COUNT
    }

    private fun commitTable() {
        tableSeq++
        _tableInfoUpdates.value = tableInfo()
        _discoveredChassis.value = listOf(fakeDevice)
        _connectionState.value = _connectionState.value.copy(device = fakeDevice)
    }

    private fun tableInfo(): SmartChassisTableInfo {
        val bytes = records.flatMap { record ->
            SmartChassisCodec.encodeSlotRecordForTable(record).asIterable()
        }.toByteArray()
        return SmartChassisTableInfo(
            tableSeq = tableSeq,
            crc16 = SmartChassisCodec.crc16CcittFalse(bytes),
            slotCount = SmartChassisProtocol.SLOT_COUNT
        )
    }

    private fun advertisementFlags(): Int {
        var flags = 0
        if (records.any { it.isEmpty }) {
            flags = flags or SmartChassisProtocol.ADV_HAS_UNBOUND_SLOT
        }
        if (activeLightStatus.mode != SmartChassisLightMode.OFF) {
            flags = flags or SmartChassisProtocol.ADV_LIGHT_ACTIVE
        }
        return flags
    }

    private fun normalizedRecord(record: SmartChassisSlotRecord, slot: Int): SmartChassisSlotRecord {
        val bytes = SmartChassisCodec.encodeSlotRecord(
            slot = slot,
            partId = record.partId,
            quantity = record.quantity,
            flags = record.flags
        )
        return SmartChassisCodec.parseSlotRecord(bytes) ?: emptyRecord()
    }

    private fun SmartChassisSlotRecord.renumber(slot: Int): SmartChassisSlotRecord {
        return if (isEmpty) {
            emptyRecord()
        } else {
            normalizedRecord(this, slot)
        }
    }

    private fun emptyRecord(): SmartChassisSlotRecord {
        return SmartChassisSlotRecord(
            slot = 0,
            partId = "",
            quantity = 0,
            flags = 0,
            crc8 = 0
        )
    }
}
