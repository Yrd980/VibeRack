package com.viberack.app.domain.stock

import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.StorageLocationSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationStockSortPolicyTest {
    private val policy = LocationStockSortPolicy()

    @Test
    fun `supports only specification keys with multiple values`() {
        val attributes = policy.supportedSpecificationAttributes(
            items = listOf(
                item(1, specifications = mapOf("阻值" to "10K", "封装" to "0603", "品牌" to "UNI")),
                item(2, specifications = mapOf("阻值" to "20K", "封装" to "0603", "品牌" to "UNI"))
            ),
            currentSortMode = StorageLocationSortMode.NONE
        )

        assertEquals(listOf("阻值"), attributes)
    }

    @Test
    fun `keeps current specification sort key even when current items do not vary`() {
        val attributes = policy.supportedSpecificationAttributes(
            items = listOf(
                item(1, specifications = mapOf("阻值" to "10K")),
                item(2, specifications = mapOf("阻值" to "10K"))
            ),
            currentSortMode = StorageLocationSortMode.bySpecification("封装")
        )

        assertEquals(listOf("封装"), attributes)
    }

    @Test
    fun `toggle priority appends inactive target and removes active target`() {
        assertEquals(listOf("NAME", "QUANTITY"), policy.togglePriority(listOf("NAME"), "QUANTITY"))
        assertEquals(listOf("NAME"), policy.togglePriority(listOf("NAME", "QUANTITY"), "QUANTITY"))
    }

    @Test
    fun `toggle selection adds and removes inventory item id`() {
        assertEquals(setOf(1L, 2L), policy.toggleSelection(setOf(1L), 2L))
        assertEquals(setOf(1L), policy.toggleSelection(setOf(1L, 2L), 2L))
    }

    private fun item(
        id: Long,
        specifications: Map<String, String>
    ): LocationInventoryItem {
        return LocationInventoryItem(
            inventoryItemId = id,
            componentId = id,
            partNumber = "C$id",
            mpn = null,
            name = null,
            brand = null,
            packageName = null,
            category = null,
            specifications = specifications,
            quantity = 1,
            lastInboundAt = 0L
        )
    }
}
