package com.viberack.app.ui

import com.viberack.app.core.nfc.NfcLabelKind
import com.viberack.app.core.nfc.NfcLabelPayload
import com.viberack.app.feature.containers.ContainersOpenRequest

internal sealed interface PhysicalTarget {
    data class SmartChassisDevice(
        val macAddress: String,
        val batchId: Int?,
        val protoVersion: Int?
    ) : PhysicalTarget
}

internal sealed interface PhysicalTargetRoute {
    data class Containers(
        val request: ContainersOpenRequest
    ) : PhysicalTargetRoute
}

internal object PhysicalTargetRouting {
    fun smartChassisDevice(
        macAddress: String,
        batchId: Int?,
        protoVersion: Int?
    ): PhysicalTarget = PhysicalTarget.SmartChassisDevice(
        macAddress = macAddress,
        batchId = batchId,
        protoVersion = protoVersion
    )

    fun fromNfcPayload(payload: NfcLabelPayload): PhysicalTarget? {
        return when (payload.kind) {
            NfcLabelKind.LOCATION,
            NfcLabelKind.MATERIAL -> null

            NfcLabelKind.DEVICE -> {
                payload.macAddress?.let { macAddress ->
                    smartChassisDevice(
                        macAddress = macAddress,
                        batchId = payload.batchId,
                        protoVersion = payload.protoVersion
                    )
                }
            }
        }
    }

    fun toRoute(target: PhysicalTarget): PhysicalTargetRoute {
        return when (target) {
            is PhysicalTarget.SmartChassisDevice -> PhysicalTargetRoute.Containers(
                ContainersOpenRequest(
                    macAddress = target.macAddress,
                    batchId = target.batchId,
                    protoVersion = target.protoVersion
                )
            )
        }
    }
}
