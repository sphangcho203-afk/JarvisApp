package com.seongja.jarvis

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

internal class JarvisSoundEngine {
    private val handler = Handler(Looper.getMainLooper())
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 34) }.getOrNull()

    fun boot() = play(ToneGenerator.TONE_PROP_ACK, 90)

    fun processing() = play(ToneGenerator.TONE_PROP_BEEP, 45)

    fun success() = play(ToneGenerator.TONE_PROP_ACK, 65)

    fun timerComplete() {
        play(ToneGenerator.TONE_PROP_BEEP2, 150)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_BEEP2, 150) }, 230L)
        handler.postDelayed({ play(ToneGenerator.TONE_PROP_ACK, 220) }, 460L)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { tone?.release() }
    }

    private fun play(toneType: Int, durationMs: Int) {
        runCatching { tone?.startTone(toneType, durationMs) }
    }
}
