package com.viberack.app.data.repository

import com.viberack.app.core.database.entity.StorageLocationEntity
import com.viberack.app.core.database.model.DashboardSummaryProjection
import com.viberack.app.core.database.model.ExistingStockLocationProjection
import com.viberack.app.core.database.model.LocationCategoryProfileProjection
import com.viberack.app.core.database.model.LocationInventoryProjection
import com.viberack.app.core.database.model.SearchInventoryProjection
import com.viberack.app.core.database.model.StorageLocationSummaryProjection
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.DashboardSummary
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.SearchInventoryRecord
import com.viberack.app.domain.model.StockLocationCell
import com.viberack.app.domain.model.StorageLocation
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

    fun toStorageLocation(entity: StorageLocationEntity): StorageLocation {
        return StorageLocation(
            id = entity.id,
            code = entity.code,
            displayName = entity.displayName,
            colorHex = entity.colorHex,
            sortMode = entity.sortMode,
            remark = entity.remark
        )
    }

    fun toStockLocationCell(projection: StorageLocationSummaryProjection): StockLocationCell {
        return StockLocationCell(
            id = projection.id,
            code = projection.code,
            displayName = projection.displayName,
            colorHex = projection.colorHex,
            sortMode = projection.sortMode,
            remark = projection.remark,
            inventoryItemCount = projection.inventoryItemCount,
            totalQuantity = projection.totalQuantity
        )
    }

    fun toLocationInventoryItem(projection: LocationInventoryProjection): LocationInventoryItem {
        return LocationInventoryItem(
            inventoryItemId = projection.inventoryItemId,
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
            imageUrl = null,
            quantity = projection.quantity,
            lastInboundAt = projection.lastInboundAt
        )
    }

    fun toLocationCategoryProfile(projection: LocationCategoryProfileProjection): LocationCategoryProfile {
        return LocationCategoryProfile(
            locationId = projection.locationId,
            category = projection.category,
            packageName = projection.packageName,
            quantity = projection.quantity
        )
    }

    fun toSearchInventoryRecord(projection: SearchInventoryProjection): SearchInventoryRecord {
        val containerType = projection.containerType.toContainerType()
        return SearchInventoryRecord(
            inventoryItemId = projection.inventoryItemId,
            stockItemId = projection.stockItemId,
            isLegacyEditable = projection.legacyInventoryItemId != null &&
                projection.containerType == ContainerType.LEGACY_LOCATION.name,
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

    fun toExistingStockLocation(projection: ExistingStockLocationProjection): ExistingStockLocation {
        return ExistingStockLocation(
            locationCode = projection.locationCode,
            locationDisplayName = projection.locationDisplayName,
            quantity = projection.quantity
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
