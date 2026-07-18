package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XCameraVoiceCommandParserTest {
    @Test
    fun closeYourEyesClosesTheWorkspace() {
        assertTrue(
            XCameraVoiceCommandParser.parse("FRIDAY, close your eyes") is
                XCameraVoiceCommand.Close
        )
    }

    @Test
    fun lookAtMeUsesFrontLensAndScans() {
        val command = XCameraVoiceCommandParser.parse(
            "Look at me and tell me what you can see"
        )
        assertTrue(command is XCameraVoiceCommand.UseFront)
        command as XCameraVoiceCommand.UseFront
        assertTrue(command.scan)
    }

    @Test
    fun frontCameraAloneSwitchesWithoutForcedScan() {
        val command = XCameraVoiceCommandParser.parse("switch to front camera")
        assertTrue(command is XCameraVoiceCommand.UseFront)
        command as XCameraVoiceCommand.UseFront
        assertFalse(command.scan)
    }

    @Test
    fun scanAgainKeepsCurrentLens() {
        assertTrue(
            XCameraVoiceCommandParser.parse("scan again and read the text") is
                XCameraVoiceCommand.Scan
        )
    }

    @Test
    fun unknownSpeechDoesNotTriggerCameraActions() {
        assertTrue(
            XCameraVoiceCommandParser.parse("that looks interesting") is
                XCameraVoiceCommand.Ignore
        )
    }
}
