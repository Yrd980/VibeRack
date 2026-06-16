package com.viberack.app.data.repository

import android.util.Log
import com.viberack.app.core.database.dao.ComponentDao
import com.viberack.app.core.database.dao.InventoryItemDao
import com.viberack.app.core.database.entity.ComponentEntity
import com.viberack.app.domain.model.InboundRecord
import org.json.JSONObject

internal class InventoryComponentWriter(
    private val componentDao: ComponentDao,
    private val inventoryItemDao: InventoryItemDao,
    private val protocolPartIdStrategy: ProtocolPartIdStrategy
) {
    suspend fun upsert(record: InboundRecord): Long {
        val normalizedPartNumber = record.component.partNumber.trim().uppercase()
        val existing = componentDao.findByPartNumber(normalizedPartNumber)
        val specJson = record.component.specifications
            .takeIf { it.isNotEmpty() }
            ?.let { JSONObject(it).toString() }
        if (existing != null) {
            val shouldResetStaleManualComponent = record.sourceType == "MANUAL_INPUT" &&
                inventoryItemDao.countByComponent(existing.id) == 0
            val protocolPartId = existing.protocolPartId
                ?: protocolPartIdStrategy.forComponent(
                    componentId = existing.id,
                    partNumber = normalizedPartNumber,
                    sourceType = record.sourceType
                )
            val updated = if (shouldResetStaleManualComponent) {
                existing.copy(
                    partNumber = normalizedPartNumber,
                    protocolPartId = protocolPartIdStrategy.forComponent(
                        componentId = existing.id,
                        partNumber = normalizedPartNumber,
                        sourceType = record.sourceType
                    ),
                    mpn = record.component.mpn,
                    name = record.component.name,
                    brand = record.component.brand,
                    packageName = record.component.packageName,
                    category = record.component.category,
                    specJson = specJson,
                    description = record.component.description,
                    sourceUrl = record.component.productUrl,
                    imageLocalPath = record.component.imageLocalPath,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                existing.copy(
                    partNumber = normalizedPartNumber,
                    protocolPartId = protocolPartId,
                    mpn = existing.mpn ?: record.component.mpn,
                    name = existing.name ?: record.component.name,
                    brand = existing.brand ?: record.component.brand,
                    packageName = existing.packageName ?: record.component.packageName,
                    category = existing.category ?: record.component.category,
                    specJson = existing.specJson ?: specJson,
                    description = existing.description ?: record.component.description,
                    sourceUrl = existing.sourceUrl ?: record.component.productUrl,
                    imageLocalPath = existing.imageLocalPath ?: record.component.imageLocalPath,
                    updatedAt = System.currentTimeMillis()
                )
            }
            if (shouldResetStaleManualComponent) {
                Log.d(
                    TAG,
                    "upsertComponent reset stale manual component partNumber=$normalizedPartNumber, existingId=${existing.id}, previousImage=${existing.imageLocalPath}, newImage=${record.component.imageLocalPath}"
                )
            }
            if (updated != existing) {
                componentDao.update(updated)
            }
            return existing.id
        }

        val componentEntity = ComponentEntity(
            partNumber = normalizedPartNumber,
            protocolPartId = protocolPartIdStrategy.forComponent(
                componentId = null,
                partNumber = normalizedPartNumber,
                sourceType = record.sourceType
            ),
            mpn = record.component.mpn,
            name = record.component.name,
            brand = record.component.brand,
            packageName = record.component.packageName,
            category = record.component.category,
            specJson = specJson,
            description = record.component.description,
            sourceUrl = record.component.productUrl,
            imageLocalPath = record.component.imageLocalPath
        )

        val insertId = componentDao.insert(componentEntity)
        if (insertId > 0) {
            val resolvedProtocolPartId = protocolPartIdStrategy.forComponent(
                componentId = insertId,
                partNumber = normalizedPartNumber,
                sourceType = record.sourceType
            )
            if (resolvedProtocolPartId != componentEntity.protocolPartId) {
                componentDao.update(
                    componentEntity.copy(
                        id = insertId,
                        protocolPartId = resolvedProtocolPartId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            return insertId
        }

        return componentDao.findByPartNumber(normalizedPartNumber)?.id
            ?: error("Failed to resolve component id for $normalizedPartNumber")
    }

    private companion object {
        private const val TAG = "InventoryComponentWriter"
    }
}
