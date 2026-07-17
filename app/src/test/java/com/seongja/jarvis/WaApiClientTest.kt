package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaApiClientTest {
    @Test
    fun normalizesInternationalPhoneNumbers() {
        assertEquals("919876543210", WaApiClient.normalizePhoneNumber("+91 98765 43210"))
        assertEquals("447700900123", WaApiClient.normalizePhoneNumber("0044-7700-900123"))
    }

    @Test
    fun recognizesDirectRecipientsAndChatIds() {
        assertTrue(WaApiClient.supportsDirectRecipient("+91 98765 43210"))
        assertTrue(WaApiClient.supportsDirectRecipient("919876543210@c.us"))
        assertTrue(WaApiClient.supportsDirectRecipient("120363000000000@g.us"))
        assertFalse(WaApiClient.supportsDirectRecipient("Alex"))
    }
}
