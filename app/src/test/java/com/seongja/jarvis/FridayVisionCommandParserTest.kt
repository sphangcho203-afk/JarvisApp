package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayVisionCommandParserTest {
    @Test
    fun openYourEyesLaunchesRearCamera() {
        val command = FridayVisionCommandParser.parse("FRIDAY, open your eyes")
        assertNotNull(command)
        assertTrue(command is FridayVisionCommand.Open)
        assertFalse((command as FridayVisionCommand.Open).frontCamera)
    }

    @Test
    fun lookAtMeUsesFrontCamera() {
        val command = FridayVisionCommandParser.parse("Look at me and tell me what you see")
        assertTrue(command is FridayVisionCommand.Open)
        assertTrue((command as FridayVisionCommand.Open).frontCamera)
    }

    @Test
    fun closeYourEyesClosesVision() {
        assertTrue(FridayVisionCommandParser.parse("close your eyes") is FridayVisionCommand.Close)
    }

    @Test
    fun ordinaryConversationDoesNotOpenCamera() {
        assertNull(FridayVisionCommandParser.parse("Explain how human vision works"))
    }
}
