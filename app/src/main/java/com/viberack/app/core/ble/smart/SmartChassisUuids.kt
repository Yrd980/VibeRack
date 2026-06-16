package com.viberack.app.core.ble.smart

import java.util.UUID

object SmartChassisUuids {
    val bindingService: UUID = UUID.fromString("7f4b0001-8d1a-4d45-9a4e-2b4a7c000000")
    val bindingControlPoint: UUID = UUID.fromString("7f4b1001-8d1a-4d45-9a4e-2b4a7c000000")
    val tableInfo: UUID = UUID.fromString("7f4b1002-8d1a-4d45-9a4e-2b4a7c000000")
    val lightService: UUID = UUID.fromString("7f4b0002-8d1a-4d45-9a4e-2b4a7c000000")
    val lightCommand: UUID = UUID.fromString("7f4b2001-8d1a-4d45-9a4e-2b4a7c000000")
    val lightStatus: UUID = UUID.fromString("7f4b2002-8d1a-4d45-9a4e-2b4a7c000000")
}
