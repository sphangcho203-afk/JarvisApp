package com.seongja.jarvis

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Suppresses short OEM speech-recognizer start/end tones without keeping media
 * muted during the actual listening session. Original volumes are restored
 * after each narrow suppression window.
 */
internal class SpeechCueSilencer(context: Context) {
    private data class SavedStream(
        val stream: Int,
        val volume: Int,
        val wasMuted: Boolean
    )

    private val audio = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var saved = emptyList<SavedStream>()

    @Synchronized
    fun suppress(durationMs: Long = DEFAULT_WINDOW_MS) {
        handler.removeCallbacksAndMessages(RESTORE_TOKEN)
        if (saved.isEmpty()) {
            saved = STREAMS.map { stream ->
                SavedStream(
                    stream = stream,
                    volume = runCatching { audio.getStreamVolume(stream) }.getOrDefault(0),
                    wasMuted = runCatching { audio.isStreamMute(stream) }.getOrDefault(false)
                )
            }
        }
        saved.forEach { state ->
            if (!state.wasMuted && state.volume > 0) {
                runCatching { audio.setStreamVolume(state.stream, 0, 0) }
            }
        }
        handler.postAtTime(
            { restore() },
            RESTORE_TOKEN,
            SystemClock.uptimeMillis() + durationMs.coerceIn(120L, 900L)
        )
    }

    @Synchronized
    fun restore() {
        handler.removeCallbacksAndMessages(RESTORE_TOKEN)
        val restoreStates = saved
        saved = emptyList()
        restoreStates.forEach { state ->
            if (!state.wasMuted) {
                runCatching { audio.setStreamVolume(state.stream, state.volume, 0) }
            }
        }
    }

    fun release() {
        restore()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private val RESTORE_TOKEN = Any()
        private val STREAMS = intArrayOf(
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_MUSIC
        )
        private const val DEFAULT_WINDOW_MS = 420L
    }
}
