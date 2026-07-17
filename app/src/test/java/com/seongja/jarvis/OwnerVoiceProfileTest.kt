package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerVoiceProfileTest {
    @Test
    fun extractsUsableFeaturesWithoutKeepingRawAudio() {
        val accumulator = PcmFeatureAccumulator(16_000)
        val pcm = ByteArray(16_000 * 2) { index ->
            if ((index / 2) % 20 < 10) 40.toByte() else (-40).toByte()
        }
        accumulator.accept(pcm)
        val sample = accumulator.finish("wake up jarvis")
        assertTrue(sample.durationMs >= 900L)
        assertTrue(sample.zeroCrossingRate >= 0f)
        assertEquals(7, sample.values().size)
        assertTrue(sample.isUsable())
    }

    @Test
    fun requiresSeveralEnrollmentSamples() {
        val sample = VoiceFeatureVector(.1f, .08f, .12f, .2f, .7f, 1_200L, 110f)
        assertFalse(OwnerVoiceMatcher.build(listOf(sample), listOf("one")).enrolled)
        assertTrue(
            OwnerVoiceMatcher.build(
                listOf(sample, sample, sample, sample),
                listOf("one", "two", "three", "four")
            ).enrolled
        )
    }

    @Test
    fun similarSamplesScoreHigherThanDifferentSamples() {
        val base = VoiceFeatureVector(.12f, .08f, .11f, .2f, .72f, 1_200L, 115f)
        val profile = OwnerVoiceMatcher.build(listOf(base, base, base, base), listOf("sample"))
        val similar = OwnerVoiceMatcher.similarity(profile, base.copy(rms = .125f, wordsPerMinute = 118f))
        val different = OwnerVoiceMatcher.similarity(
            profile,
            VoiceFeatureVector(.5f, .45f, .8f, .9f, .05f, 8_000L, 280f)
        )
        assertTrue(similar > different)
        assertTrue(similar >= .75f)
    }

    @Test
    fun familiarityNeverAuthorizesSensitiveActions() {
        assertFalse(OwnerVoiceAuthorizationPolicy.mayAuthorizeSensitiveAction())
    }

    @Test
    fun routesOwnerVoiceCommandsOnly() {
        assertEquals(OwnerVoiceCommand.Enroll, OwnerVoiceCommandParser.parse("Train my owner voice profile"))
        assertEquals(OwnerVoiceCommand.Status, OwnerVoiceCommandParser.parse("What is my voice identity status"))
        assertEquals(OwnerVoiceCommand.OpenLab, OwnerVoiceCommandParser.parse("Open the voice lab"))
        assertNull(OwnerVoiceCommandParser.parse("Open Spotify"))
    }
}
