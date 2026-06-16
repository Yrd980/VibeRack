package com.viberack.app.core.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NfcLabelPayloadCodecTest {
    @Test
    fun deviceUriRoundTripsWithNormalizedMacAddress() {
        val uri = NfcLabelPayloadCodec.deviceUri(
            macAddress = "aa:bb:cc:dd:ee:ff",
            batchId = 1001,
            protoVersion = 1
        )

        val payload = NfcLabelPayloadCodec.parse(uri)

        assertNotNull(payload)
        assertEquals(NfcLabelKind.DEVICE, payload!!.kind)
        assertEquals("AA:BB:CC:DD:EE:FF", payload.macAddress)
        assertEquals(1001, payload.batchId)
        assertEquals(1, payload.protoVersion)
    }

    @Test
    fun rejectsInvalidDeviceUriFields() {
        assertNull(NfcLabelPayloadCodec.parse("lcscerp://device?mac=AA:BB&batch=1001&ver=1"))
        assertNull(NfcLabelPayloadCodec.parse("lcscerp://device?mac=AA:BB:CC:DD:EE:FF&batch=70000&ver=1"))
        assertNull(NfcLabelPayloadCodec.parse("lcscerp://device?mac=AA:BB:CC:DD:EE:FF&batch=1&ver=300"))
    }
}
