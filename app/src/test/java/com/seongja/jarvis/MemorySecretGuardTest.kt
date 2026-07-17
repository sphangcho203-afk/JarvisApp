package com.seongja.jarvis

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemorySecretGuardTest {
    @Test
    fun blocksAuthenticationSecrets() {
        assertNotNull(MemorySecretGuard.rejectionReason("OTP 482193"))
        assertNotNull(MemorySecretGuard.rejectionReason("password: hunter-example"))
        assertNotNull(MemorySecretGuard.rejectionReason("Authorization: Bearer abcdefghijklmnopqrstuvwxyz"))
        assertNotNull(MemorySecretGuard.rejectionReason("sk-proj-examplekey123456789"))
        assertNotNull(MemorySecretGuard.rejectionReason("-----BEGIN PRIVATE KEY-----"))
    }

    @Test
    fun allowsOrdinaryPersonalMemory() {
        assertNull(MemorySecretGuard.rejectionReason("I prefer concise technical explanations."))
        assertNull(MemorySecretGuard.rejectionReason("My current project is F.R.I.D.A.Y."))
    }
}
