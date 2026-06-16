package com.viberack.app.core.ble.smart

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build

internal object SmartChassisGattDescriptorWriter {
    fun writeEnableNotification(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ): DescriptorWriteStart {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
            if (status == BluetoothStatusCodes.SUCCESS) {
                DescriptorWriteStart.Started
            } else {
                DescriptorWriteStart.Failed("Notification descriptor write failed to start: $status")
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            if (gatt.writeDescriptor(descriptor)) {
                DescriptorWriteStart.Started
            } else {
                DescriptorWriteStart.Failed("Notification descriptor write failed to start")
            }
        }
    }
}

internal sealed interface DescriptorWriteStart {
    data object Started : DescriptorWriteStart
    data class Failed(val message: String) : DescriptorWriteStart
}
