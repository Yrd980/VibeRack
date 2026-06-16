package com.viberack.app.core.ble.smart

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSmartChassisClientTest {
    @Test
    fun writeReadAndClearAdvanceTableSeq() = runBlocking {
        val client = FakeSmartChassisClient()
        val device = (client.connect("FA:KE:00:00:00:01") as SmartChassisClientResult.Success).value
        val initialInfo = (client.readTableInfo() as SmartChassisClientResult.Success).value

        assertEquals(1, device.advertisement.tableSeqLow16)

        val writeInfo = (client.writeOne(record(slot = 3, partId = "C100", quantity = 12))
            as SmartChassisClientResult.Success).value
        assertEquals(initialInfo.tableSeq + 1, writeInfo.tableSeq)

        val readRecord = (client.readOne(3) as SmartChassisClientResult.Success).value
        assertEquals("C100", readRecord.partId)
        assertEquals(12, readRecord.quantity)

        val clearInfo = (client.clearOne(3) as SmartChassisClientResult.Success).value
        assertEquals(writeInfo.tableSeq + 1, clearInfo.tableSeq)
        assertTrue((client.readOne(3) as SmartChassisClientResult.Success).value.isEmpty)
    }

    @Test
    fun insertRemoveAndMoveBlockReflowRecords() = runBlocking {
        val client = FakeSmartChassisClient(
            initialRecords = listOf(
                record(slot = 1, partId = "C1", quantity = 1),
                record(slot = 2, partId = "C2", quantity = 2),
                record(slot = 3, partId = "C3", quantity = 3)
            )
        )
        client.connect("FA:KE:00:00:00:01")

        client.insertAt(2, record(slot = 2, partId = "MNEW", quantity = 9))
        assertEquals(listOf("C1", "MNEW", "C2", "C3"), occupiedPartIds(client))

        client.removeAt(3)
        assertEquals(listOf("C1", "MNEW", "C3"), occupiedPartIds(client))

        client.moveBlock(from = 1, to = 3, length = 1)
        val snapshot = (client.readAll() as SmartChassisClientResult.Success).value
        assertEquals(listOf("MNEW", "C3", "C1"), snapshot.records.filterNot { it.isEmpty }.map { it.partId })
        assertEquals(listOf(1, 2, 3), snapshot.records.filterNot { it.isEmpty }.map { it.slot })
    }

    @Test
    fun setQuantityRequiresOccupiedSlotAndLightUpdatesStatus() = runBlocking {
        val client = FakeSmartChassisClient(initialRecords = listOf(record(slot = 5, partId = "C5", quantity = 5)))
        client.connect("FA:KE:00:00:00:01")

        val failure = client.setQuantity(slot = 6, quantity = 10) as SmartChassisClientResult.Failure
        assertEquals(SmartChassisBindingOp.SET_QTY, failure.op)
        assertEquals(SmartChassisBindingStatus.ERR_PARAM, failure.status)

        client.setQuantity(slot = 5, quantity = 42)
        assertEquals(42, (client.readOne(5) as SmartChassisClientResult.Success).value.quantity)

        val lightStatus = (client.sendLightCommand(
            SmartChassisLightCommand(
                mode = SmartChassisLightMode.FIND,
                maskA = SmartChassisCodec.slotMask(5),
                colorA = RgbColor(0, 255, 0),
                timeoutSeconds = 0
            )
        ) as SmartChassisClientResult.Success).value
        assertEquals(SmartChassisLightMode.FIND, lightStatus.mode)
        assertEquals(SmartChassisProtocol.DEFAULT_LIGHT_TIMEOUT_SECONDS, lightStatus.remainingSeconds)
    }

    private suspend fun occupiedPartIds(client: FakeSmartChassisClient): List<String> {
        val snapshot = (client.readAll() as SmartChassisClientResult.Success).value
        return snapshot.records.filterNot { it.isEmpty }.map { it.partId }
    }

    private fun record(slot: Int, partId: String, quantity: Int): SmartChassisSlotRecord {
        val bytes = SmartChassisCodec.encodeSlotRecord(
            slot = slot,
            partId = partId,
            quantity = quantity,
            flags = if (partId.startsWith("M")) SmartChassisProtocol.SLOT_FLAG_CUSTOM_PART else 0
        )
        return SmartChassisCodec.parseSlotRecord(bytes)!!
    }
}
