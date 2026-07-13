package com.seongja.jarvis

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Lightweight cinematic cue engine.
 *
 * ToneGenerator keeps the APK self-contained while still giving each Jarvis
 * state a different rhythmic identity. The sequences are deliberately short
 * and restrained so repeated commands do not become exhausting.
 */
internal class JarvisSoundEngine {
    private val handler = Handler(Looper.getMainLooper())
    private val tone = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 28)
    }.getOrNull()

    fun boot() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP, 55)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 100) }, 115L)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 155) }, 285L)
    }

    fun processing() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP, 42)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_BEEP, 32) }, 105L)
    }

    fun research() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP2, 48)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_BEEP, 46) }, 120L)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 55) }, 250L)
    }

    fun speaking() {
        clearPending()
        play(ToneGenerator.TONE_PROP_ACK, 48)
    }

    fun success() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP, 45)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 95) }, 95L)
    }

    fun warning() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP2, 95)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_BEEP2, 70) }, 165L)
    }

    fun error() {
        clearPending()
        play(ToneGenerator.TONE_PROP_NACK, 130)
    }

    fun standby() {
        clearPending()
        play(ToneGenerator.TONE_PROP_ACK, 55)
    }

    fun timerComplete() {
        clearPending()
        play(ToneGenerator.TONE_PROP_BEEP2, 130)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_BEEP2, 130) }, 215L)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 210) }, 430L)
    }

    fun release() {
        clearPending()
        runCatching { tone?.release() }
    }

    private fun clearPending() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun play(toneType: Int, durationMs: Int) {
        runCatching { tone?.startTone(toneType, durationMs) }
    }
}
