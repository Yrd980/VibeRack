package com.viberack.app.data.repository

import com.viberack.app.core.database.model.SearchInventoryProjection
import com.viberack.app.domain.model.ContainerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryReadModelMapperTest {
    @Test
    fun mapsSearchProjectionFieldsAndDefaultsMalformedSpecsToEmptyMap() {
        val record = InventoryReadModelMapper.toSearchInventoryRecord(
            searchProjection(
                legacyInventoryItemId = 77,
                containerType = ContainerType.LEGACY_LOCATION.name
            )
        )

        assertEquals("C123", record.partNumber)
        assertEquals("A1", record.locationCode)
        assertEquals(5, record.quantity)
        assertEquals(emptyMap<String, String>(), record.specifications)
    }

    @Test
    fun defaultsMalformedSpecificationJsonToEmptyMap() {
        assertEquals(emptyMap<String, String>(), InventoryReadModelMapper.parseSpecifications("{bad json"))
    }

    @Test
    fun mapsLegacySearchProjectionAsEditableAndUnknownTypeAsReadOnlyLegacyContainer() {
        val legacy = searchProjection(
            legacyInventoryItemId = 77,
            containerType = ContainerType.LEGACY_LOCATION.name
        )
        val unknown = searchProjection(
            legacyInventoryItemId = 77,
            containerType = "UNKNOWN"
        )

        assertTrue(InventoryReadModelMapper.toSearchInventoryRecord(legacy).isLegacyEditable)
        with(InventoryReadModelMapper.toSearchInventoryRecord(unknown)) {
            assertEquals(ContainerType.LEGACY_LOCATION, containerType)
            assertFalse(isLegacyEditable)
        }
    }

    private fun searchProjection(
        legacyInventoryItemId: Long?,
        containerType: String
    ): SearchInventoryProjection {
        return SearchInventoryProjection(
            inventoryItemId = 1,
            legacyInventoryItemId = legacyInventoryItemId,
            stockItemId = 2,
            componentId = 3,
            partNumber = "C123",
            mpn = null,
            name = null,
            brand = null,
            packageName = null,
            category = null,
            description = null,
            sourceUrl = null,
            specJson = null,
            imageLocalPath = null,
            quantity = 5,
            locationId = 6,
            locationCode = "A1",
            locationDisplayName = "A1",
            locationColorHex = null,
            containerType = containerType,
            containerMacAddress = null,
            slotId = 7,
            slotNumber = 1,
            slotCode = "A1",
            slotDisplayName = "A1"
        )
    }
}
