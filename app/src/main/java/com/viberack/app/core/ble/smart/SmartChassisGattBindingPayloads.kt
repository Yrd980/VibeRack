package com.viberack.app.core.ble.smart

internal object SmartChassisGattBindingPayloads {
    fun encode(block: () -> ByteArray): ByteArray? {
        return runCatching { block() }.getOrNull()
    }

    fun encodeFailure(op: SmartChassisBindingOp): SmartChassisClientResult.Failure {
        return SmartChassisClientResult.Failure(
            message = "Invalid binding command payload",
            op = op,
            status = SmartChassisBindingStatus.ERR_PARAM
        )
    }
}
