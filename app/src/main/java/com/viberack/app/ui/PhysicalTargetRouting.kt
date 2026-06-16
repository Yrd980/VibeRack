package com.viberack.app.ui

import com.viberack.app.core.nfc.NfcLabelKind
import com.viberack.app.core.nfc.NfcLabelPayload
import com.viberack.app.feature.boxes.BoxesOpenRequest
import com.viberack.app.feature.containers.ContainersOpenRequest
import com.viberack.app.feature.inventory.InventoryOpenRequest

internal sealed interface PhysicalTarget {
    data class InventoryItem(
        val locationCode: String,
        val partNumber: String
    ) : PhysicalTarget

    data class InventoryLocation(
        val locationCode: String
    ) : PhysicalTarget

    data class InventoryPartNumber(
        val partNumber: String
    ) : PhysicalTarget

    data class BoxLayer(
        val boxCode: String,
        val layerCode: String
    ) : PhysicalTarget

    data class SmartChassisDevice(
        val macAddress: String,
        val batchId: Int?,
        val protoVersion: Int?
    ) : PhysicalTarget
}

internal sealed interface PhysicalTargetRoute {
    data class Inventory(
        val request: InventoryOpenRequest
    ) : PhysicalTargetRoute

    data class Boxes(
        val request: BoxesOpenRequest
    ) : PhysicalTargetRoute

    data class Containers(
        val request: ContainersOpenRequest
    ) : PhysicalTargetRoute
}

internal object PhysicalTargetRouting {
    fun inventoryItem(
        locationCode: String,
        partNumber: String
    ): PhysicalTarget = PhysicalTarget.InventoryItem(
        locationCode = locationCode,
        partNumber = partNumber
    )

    fun inventoryLocation(locationCode: String): PhysicalTarget {
        return PhysicalTarget.InventoryLocation(locationCode = locationCode)
    }

    fun inventoryPartNumber(partNumber: String): PhysicalTarget {
        return PhysicalTarget.InventoryPartNumber(partNumber = partNumber)
    }

    fun boxLayer(
        boxCode: String,
        layerCode: String
    ): PhysicalTarget = PhysicalTarget.BoxLayer(
        boxCode = boxCode,
        layerCode = layerCode
    )

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
            NfcLabelKind.LOCATION -> {
                payload.locationCode?.let(::inventoryLocation)
            }

            NfcLabelKind.MATERIAL -> {
                val boxCode = payload.boxCode
                val layerCode = payload.layerCode
                val locationCode = payload.locationCode
                val partNumber = payload.partNumber
                when {
                    boxCode != null && layerCode != null -> boxLayer(boxCode, layerCode)
                    locationCode != null && partNumber != null -> inventoryItem(locationCode, partNumber)
                    partNumber != null -> inventoryPartNumber(partNumber)
                    else -> null
                }
            }

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
            is PhysicalTarget.InventoryItem -> PhysicalTargetRoute.Inventory(
                InventoryOpenRequest(
                    locationCode = target.locationCode,
                    partNumber = target.partNumber
                )
            )

            is PhysicalTarget.InventoryLocation -> PhysicalTargetRoute.Inventory(
                InventoryOpenRequest(locationCode = target.locationCode)
            )

            is PhysicalTarget.InventoryPartNumber -> PhysicalTargetRoute.Inventory(
                InventoryOpenRequest(partNumber = target.partNumber)
            )

            is PhysicalTarget.BoxLayer -> PhysicalTargetRoute.Boxes(
                BoxesOpenRequest(
                    boxCode = target.boxCode,
                    layerCode = target.layerCode
                )
            )

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
