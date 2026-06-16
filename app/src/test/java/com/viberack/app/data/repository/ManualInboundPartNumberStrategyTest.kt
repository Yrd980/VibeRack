package com.viberack.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualInboundPartNumberStrategyTest {
    @Test
    fun startsAtC01WhenNoExistingManualPartNumberExists() {
        assertEquals("C01", ManualInboundPartNumberStrategy.nextPartNumber(emptyList()))
    }

    @Test
    fun incrementsLargestValidC0Index() {
        assertEquals(
            "C010",
            ManualInboundPartNumberStrategy.nextPartNumber(listOf("C01", "c09", "C123456", "M000000001"))
        )
    }

    @Test
    fun parsesOnlyC0PrefixedNumericPartNumbers() {
        assertEquals(12, ManualInboundPartNumberStrategy.parseIndex(" c012 "))
        assertNull(ManualInboundPartNumberStrategy.parseIndex("C123456"))
        assertNull(ManualInboundPartNumberStrategy.parseIndex("C0ABC"))
        assertNull(ManualInboundPartNumberStrategy.parseIndex(null))
    }
}
