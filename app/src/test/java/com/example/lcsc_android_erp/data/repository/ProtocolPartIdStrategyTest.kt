package com.example.lcsc_android_erp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolPartIdStrategyTest {
    private val strategy = ProtocolPartIdStrategy()

    @Test
    fun usesCatalogCIdWhenValidAndNotManual() {
        assertEquals("C123456", strategy.forComponent(componentId = 42, partNumber = " c123456 "))
    }

    @Test
    fun generatesManualIdForManualInputAndC0Placeholders() {
        assertEquals("M000000042", strategy.forComponent(42, "C0123", "MANUAL_INPUT"))
        assertEquals("M000000042", strategy.forComponent(42, "C0123"))
    }

    @Test
    fun generatesManualIdForSmartSlotInboundCustomInput() {
        assertEquals("C123456", strategy.forComponent(42, "C123456", "SMART_SLOT_INBOUND"))
        assertEquals("M000000042", strategy.forComponent(42, "MLOCAL", "SMART_SLOT_INBOUND"))
        assertNull(strategy.forComponent(null, "MLOCAL", "SMART_SLOT_INBOUND"))
    }

    @Test
    fun rejectsInvalidOrOverlongProtocolIds() {
        assertNull(strategy.normalize("C1234567890"))
        assertNull(strategy.normalize("X123"))
        assertFalse(strategy.isValid("C1234567890"))
        assertTrue(strategy.isValid("M000000042"))
    }

    @Test
    fun normalizesValidProtocolIds() {
        assertEquals("MABC123", strategy.normalize(" mabc123 "))
    }
}
