package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDiaryCommandTest {
    @Test
    fun opensPrivateDiaryFromNaturalCommands() {
        assertEquals(PrivateDiaryCommand.Open, PrivateDiaryCommandParser.parse("Open my private diary"))
        assertEquals(PrivateDiaryCommand.Open, PrivateDiaryCommandParser.parse("Friday, bring the diary vault"))
    }

    @Test
    fun createsAndSearchesEntries() {
        assertEquals(
            PrivateDiaryCommand.NewEntry,
            PrivateDiaryCommandParser.parse("Create a new diary entry")
        )
        assertEquals(
            PrivateDiaryCommand.Search("the gmail recovery build"),
            PrivateDiaryCommandParser.parse("Find my diary entries about the Gmail recovery build")
        )
    }

    @Test
    fun securesVaultWithoutOpeningIt() {
        assertEquals(
            PrivateDiaryCommand.SecureVault,
            PrivateDiaryCommandParser.parse("Secure the vault")
        )
        assertEquals(
            PrivateDiaryCommand.SecureVault,
            PrivateDiaryCommandParser.parse("Lock the diary")
        )
    }

    @Test
    fun ignoresUnrelatedCommands() {
        assertNull(PrivateDiaryCommandParser.parse("Open Spotify"))
        assertNull(PrivateDiaryCommandParser.parse("Show the latest news"))
    }

    @Test
    fun ownerOnlyEntriesRemainUnreadableToFriday() {
        assertFalse(DiaryAiAccess.OWNER_ONLY.fridayMayReadWithoutPrompt())
        assertFalse(DiaryAiAccess.OWNER_ONLY.mayEnterMemory())
        assertFalse(DiaryAiAccess.ASK_EVERY_TIME.fridayMayReadWithoutPrompt())
        assertFalse(DiaryAiAccess.SESSION_READABLE.mayEnterMemory())
        assertTrue(DiaryAiAccess.SESSION_READABLE.fridayMayReadWithoutPrompt())
        assertTrue(DiaryAiAccess.MEMORY_APPROVED.fridayMayReadWithoutPrompt())
        assertTrue(DiaryAiAccess.MEMORY_APPROVED.mayEnterMemory())
    }
}
