package com.viberack.app.core.ble.smart

internal class SmartChassisGattBindingNotificationHandler(
    private val getPendingReadAll: () -> PendingReadAll?,
    private val setPendingReadAll: (PendingReadAll?) -> Unit,
    private val getPendingBindingOp: () -> PendingBindingOp?,
    private val setPendingBindingOp: (PendingBindingOp?) -> Unit
) {
    fun handle(value: ByteArray) {
        val result = SmartChassisCodec.parseBindingResult(value) ?: return
        val readAll = getPendingReadAll()
        if (readAll != null && result.op == SmartChassisBindingOp.READ_ALL) {
            handleReadAllResult(result, readAll)
            return
        }

        val pending = getPendingBindingOp() ?: return
        if (pending.op != result.op) {
            return
        }
        setPendingBindingOp(null)
        if (result.status == SmartChassisBindingStatus.OK) {
            pending.deferred.complete(
                SmartChassisClientResult.Success(
                    value = result.payload,
                    op = result.op,
                    status = result.status
                )
            )
        } else {
            pending.deferred.complete(
                SmartChassisClientResult.Failure(
                    message = "Binding command failed: ${result.status}",
                    op = result.op,
                    status = result.status
                )
            )
        }
    }

    private fun handleReadAllResult(
        result: SmartChassisBindingResult,
        readAll: PendingReadAll
    ) {
        if (result.status != SmartChassisBindingStatus.OK) {
            setPendingReadAll(null)
            readAll.deferred.complete(
                SmartChassisClientResult.Failure(
                    message = "READ_ALL failed: ${result.status}",
                    op = SmartChassisBindingOp.READ_ALL,
                    status = result.status
                )
            )
            return
        }
        if (SmartChassisCodec.isReadAllEndPayload(result.payload)) {
            if (readAll.records.size != SmartChassisProtocol.SLOT_COUNT) {
                setPendingReadAll(null)
                readAll.deferred.complete(
                    SmartChassisClientResult.Failure(
                        message = "READ_ALL returned ${readAll.records.size} records, expected ${SmartChassisProtocol.SLOT_COUNT}",
                        op = SmartChassisBindingOp.READ_ALL,
                        status = SmartChassisBindingStatus.ERR_PARAM
                    )
                )
                return
            }
            setPendingReadAll(null)
            readAll.deferred.complete(
                SmartChassisClientResult.Success(
                    readAll.records.toList(),
                    SmartChassisBindingOp.READ_ALL
                )
            )
            return
        }
        val record = SmartChassisCodec.parseSlotRecord(result.payload)
        if (record == null) {
            setPendingReadAll(null)
            readAll.deferred.complete(
                SmartChassisClientResult.Failure("Invalid READ_ALL record", SmartChassisBindingOp.READ_ALL)
            )
        } else {
            readAll.records += record
        }
    }
}
