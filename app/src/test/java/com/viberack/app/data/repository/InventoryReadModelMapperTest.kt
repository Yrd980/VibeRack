package com.viberack.app.data.repository

import com.viberack.app.core.database.model.SearchInventoryProjection
import com.viberack.app.domain.model.ContainerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InventoryReadModelMapperTest {
    @Test
    fun mapsSearchProjectionFieldsAndDefaultsMalformedSpecsToEmptyMap() {
        val record = InventoryReadModelMapper.toSearchInventoryRecord(
            searchProjection(
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
    fun mapsSmartChassisSearchProjectionAsFindByLightCapable() {
        val smartChassis = searchProjection(
            containerType = ContainerType.SMART_CHASSIS.name,
            containerMacAddress = "AA:BB:CC:DD:EE:FF",
            slotNumber = 7
        )

        with(InventoryReadModelMapper.toSearchInventoryRecord(smartChassis)) {
            assertEquals(ContainerType.SMART_CHASSIS, containerType)
            assertEquals("AA:BB:CC:DD:EE:FF", containerMacAddress)
            assertEquals(7, slotNumber)
            assertEquals(true, canFindByLight)
        }
    }

    @Test
    fun mapsUnknownTypeAsLegacyContainerWithoutFindByLight() {
        val unknown = searchProjection(
            containerType = "UNKNOWN"
        )

        with(InventoryReadModelMapper.toSearchInventoryRecord(unknown)) {
            assertEquals(ContainerType.LEGACY_LOCATION, containerType)
            assertFalse(canFindByLight)
        }
    }

    private fun searchProjection(
        containerType: String,
        containerMacAddress: String? = null,
        slotNumber: Int = 1
    ): SearchInventoryProjection {
        return SearchInventoryProjection(
            inventoryItemId = 1,
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
            containerMacAddress = containerMacAddress,
            slotId = 7,
            slotNumber = slotNumber,
            slotCode = "A1",
            slotDisplayName = "A1"
        )
    }
}
