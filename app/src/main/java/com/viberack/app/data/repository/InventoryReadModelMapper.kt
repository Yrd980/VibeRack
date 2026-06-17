package com.viberack.app.data.repository

import com.viberack.app.core.database.model.DashboardSummaryProjection
import com.viberack.app.core.database.model.SearchInventoryProjection
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.SearchInventoryRecord
import org.json.JSONObject

internal object InventoryReadModelMapper {
    fun toDashboardSummary(projection: DashboardSummaryProjection): DashboardSummary {
        return DashboardSummary(
            componentCount = projection.componentCount,
            locationCount = projection.locationCount,
            inventoryCount = projection.inventoryCount,
            totalQuantity = projection.totalQuantity,
            transactionCount = projection.transactionCount
        )
    }

    fun toSearchInventoryRecord(projection: SearchInventoryProjection): SearchInventoryRecord {
        val containerType = projection.containerType.toContainerType()
        return SearchInventoryRecord(
            inventoryItemId = projection.inventoryItemId,
            stockItemId = projection.stockItemId,
            componentId = projection.componentId,
            partNumber = projection.partNumber,
            mpn = projection.mpn,
            name = projection.name,
            brand = projection.brand,
            packageName = projection.packageName,
            category = projection.category,
            description = projection.description,
            sourceUrl = projection.sourceUrl,
            specifications = parseSpecifications(projection.specJson),
            imageLocalPath = projection.imageLocalPath,
            quantity = projection.quantity,
            locationId = projection.locationId,
            locationCode = projection.locationCode,
            locationDisplayName = projection.locationDisplayName,
            locationColorHex = projection.locationColorHex,
            containerType = containerType,
            containerMacAddress = projection.containerMacAddress,
            slotId = projection.slotId,
            slotNumber = projection.slotNumber,
            slotCode = projection.slotCode,
            slotDisplayName = projection.slotDisplayName
        )
    }

    fun parseSpecifications(specJson: String?): Map<String, String> {
        if (specJson.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val json = JSONObject(specJson)
            json.keys().asSequence().associateWith { key ->
                json.optString(key)
            }.filterValues { value ->
                value.isNotBlank() && value != "null"
            }
        }.getOrDefault(emptyMap())
    }

    private fun String.toContainerType(): ContainerType {
        return runCatching { ContainerType.valueOf(this) }
            .getOrDefault(ContainerType.LEGACY_LOCATION)
    }
}
