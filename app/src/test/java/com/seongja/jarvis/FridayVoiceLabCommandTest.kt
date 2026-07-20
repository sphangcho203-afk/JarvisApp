package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayVoiceLabCommandTest {
    @Test
    fun opensVoiceLabFromNaturalCommands() {
        listOf(
            "Open voice lab",
            "FRIDAY voice lab",
            "Change your voice",
            "Choose a speech voice",
            "Open TTS settings",
            "Test the voice setup",
            "Configure speech"
        ).forEach { command ->
            assertTrue(command, FridayVoiceLabCommand.matches(command))
        }
    }

    @Test
    fun ignoresUnrelatedVoicePhrases() {
        listOf(
            "Turn on the flashlight",
            "Open API setup",
            "What is the weather?",
            "Voice message from Alex",
            "Research neural speech models"
        ).forEach { command ->
            assertFalse(command, FridayVoiceLabCommand.matches(command))
        }
    }
}
