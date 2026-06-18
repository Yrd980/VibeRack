package com.viberack.app.data.repository

import com.viberack.app.core.database.model.DashboardSummaryProjection
import com.viberack.app.core.database.model.SearchInventoryProjection
import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.SearchInventoryRecord

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
        val containerType = with(ContainerReadModels) { projection.containerType.toContainerType() }
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
            specifications = ContainerReadModels.parseSpecifications(projection.specJson),
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

    fun parseSpecifications(specJson: String?): Map<String, String> =
        ContainerReadModels.parseSpecifications(specJson)
}
