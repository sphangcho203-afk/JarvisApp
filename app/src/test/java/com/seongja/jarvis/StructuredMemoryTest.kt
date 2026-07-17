package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredMemoryTest {
    @Test
    fun routesOnlyExplicitMemoryVaultCommands() {
        assertEquals(MemoryVaultCommand.Open, MemoryVaultCommandParser.parse("Open my memory vault"))
        assertEquals(MemoryVaultCommand.ReviewPending, MemoryVaultCommandParser.parse("Review the pending memory queue"))
        assertEquals(MemoryVaultCommand.ConversationArchive, MemoryVaultCommandParser.parse("Open conversation archive in memory vault"))
        assertEquals(
            MemoryVaultCommand.Search("friday project"),
            MemoryVaultCommandParser.parse("Search my saved memories about Friday project")
        )
        assertNull(MemoryVaultCommandParser.parse("Remember that I like this"))
    }

    @Test
    fun retentionRulesExpireOnlyWhenExpected() {
        val now = 1_800_000_000_000L
        assertFalse(StructuredMemoryRecord(value = "session", retention = MemoryRetention.SESSION, updatedAtMs = now).isExpired(now))
        assertFalse(StructuredMemoryRecord(value = "permanent", retention = MemoryRetention.PERMANENT, updatedAtMs = 0L).isExpired(now))
        assertTrue(
            StructuredMemoryRecord(
                value = "old",
                retention = MemoryRetention.THIRTY_DAYS,
                updatedAtMs = now - 31L * 24L * 60L * 60L * 1000L
            ).isExpired(now)
        )
    }

    @Test
    fun diaryCandidatesRequireOwnerReviewAndRemainPrivateInitially() {
        val record = StructuredMemoryRecord(
            value = "A diary-derived candidate",
            source = MemorySource.DIARY_APPROVAL,
            sensitivity = MemorySensitivity.PRIVATE,
            reviewState = MemoryReviewState.PENDING_OWNER_REVIEW
        )
        assertEquals(MemoryReviewState.PENDING_OWNER_REVIEW, record.reviewState)
        assertEquals(MemorySource.DIARY_APPROVAL, record.source)
        assertEquals(MemorySensitivity.PRIVATE, record.sensitivity)
    }

    @Test
    fun confidenceIsBounded() {
        assertTrue(StructuredMemoryRecord(value = "verified", confidence = 1f).confidence in 0f..1f)
    }
}
