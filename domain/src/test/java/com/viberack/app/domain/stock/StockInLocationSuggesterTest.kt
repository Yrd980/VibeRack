package com.viberack.app.domain.stock

import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class StockInLocationSuggesterTest {
    private val suggester = StockInLocationSuggester()

    @Test
    fun `prefers existing stock location when it is available`() {
        val locationCode = suggester.suggestLocationCode(
            component = component(category = "电阻"),
            existingStockLocations = listOf(existingStockLocation("r2")),
            availableLocations = listOf(location("R1"), location("R2")),
            locationCategoryProfiles = emptyList(),
            fallbackCode = "A1"
        )

        assertEquals("r2", locationCode)
    }

    @Test
    fun `uses category and package profile before keyword prefix`() {
        val availableLocations = listOf(
            location("R1", id = 1),
            location("R2", id = 2),
            location("R3", id = 3)
        )
        val profiles = listOf(
            LocationCategoryProfile(locationId = 1, category = "电阻", packageName = "0603", quantity = 7),
            LocationCategoryProfile(locationId = 2, category = "电阻", packageName = "0805", quantity = 3)
        )

        val locationCode = suggester.suggestLocationCode(
            component = component(category = " 电阻 ", packageName = "0805"),
            existingStockLocations = emptyList(),
            availableLocations = availableLocations,
            locationCategoryProfiles = profiles,
            fallbackCode = "A1"
        )

        assertEquals("R2", locationCode)
    }

    @Test
    fun `falls back to keyword prefix sorted by legacy location code`() {
        val locationCode = suggester.suggestLocationCode(
            component = component(category = "贴片电容"),
            existingStockLocations = emptyList(),
            availableLocations = listOf(location("C10"), location("C2"), location("R1")),
            locationCategoryProfiles = emptyList(),
            fallbackCode = "A1"
        )

        assertEquals("C2", locationCode)
    }

    @Test
    fun `returns default when category has no mapping`() {
        val locationCode = suggester.suggestLocationCode(
            component = component(category = "工具"),
            existingStockLocations = emptyList(),
            availableLocations = listOf(location("R1")),
            locationCategoryProfiles = emptyList(),
            fallbackCode = "A1"
        )

        assertEquals("A1", locationCode)
    }

    private fun component(
        category: String?,
        packageName: String? = null
    ): ComponentDetail {
        return ComponentDetail(
            partNumber = "C123",
            mpn = null,
            name = null,
            brand = null,
            packageName = packageName,
            category = category,
            description = null,
            stockQuantity = null,
            price = null,
            productUrl = null,
            datasheetUrl = null,
            imageLocalPath = null,
            imageUrl = null,
            specifications = emptyMap()
        )
    }

    private fun location(
        code: String,
        id: Long = code.hashCode().toLong()
    ): StorageLocation {
        return StorageLocation(
            id = id,
            code = code,
            displayName = null,
            colorHex = null,
            sortMode = "LEGACY",
            remark = null
        )
    }

    private fun existingStockLocation(code: String): ExistingStockLocation {
        return ExistingStockLocation(
            locationCode = code,
            locationDisplayName = null,
            quantity = 1
        )
    }
}
