package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerIdentityCoreTest {
    @Test
    fun ownerCanBeAddressedAsBossOrSir() {
        assertTrue(OwnerIdentityCore.SYSTEM_IDENTITY.contains("Boss or Sir"))
        assertFalse(OwnerIdentityCore.SYSTEM_IDENTITY.contains("never call him Sir"))
    }

    @Test
    fun systemEnvelopeForbidsFakeWorkspaceClaims() {
        val envelope = OwnerIdentityCore.systemEnvelope(
            editablePrompt = "",
            taskDirective = "Respond to the current request.",
            memoryContext = "none"
        )
        assertTrue(envelope.contains("NATIVE WORKSPACES"))
        assertTrue(envelope.contains("Never imitate these surfaces in prose"))
        assertTrue(envelope.contains("real decoded preview"))
    }
}
