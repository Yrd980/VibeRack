package com.viberack.app.core.ble.smart

import kotlinx.coroutines.CompletableDeferred

internal data class PendingBindingOp(
    val op: SmartChassisBindingOp,
    val deferred: CompletableDeferred<SmartChassisClientResult<ByteArray>>
)

internal data class PendingReadAll(
    val deferred: CompletableDeferred<SmartChassisClientResult<List<SmartChassisSlotRecord>>>,
    val records: MutableList<SmartChassisSlotRecord> = mutableListOf()
)
