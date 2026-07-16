package com.seongja.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Compatibility endpoint for the retired Android Text-to-Speech fallback.
 *
 * FRIDAY must never fall back to a generic system voice. Cartesia Sonic or the
 * configured premium PCM gateway owns speech output. When neither is ready,
 * this endpoint completes silently and reports an explicit diagnostic instead
 * of speaking with Android TTS.
 */
class LocalJarvisVoice(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReady(label: String)
        fun onStarted(label: String)
        fun onCompleted()
        fun onError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false

    init {
        context.applicationContext
        handler.post { listener.onReady(LABEL) }
    }

    fun isReady(): Boolean = false

    fun label(): String = LABEL

    fun speak(text: String): Boolean {
        if (destroyed || JarvisResponseSanitizer.spoken(text).isBlank()) return false
        handler.post {
            if (!destroyed) {
                listener.onError("Android text-to-speech is disabled. Configure Cartesia for FRIDAY voice output.")
            }
        }
        return true
    }

    fun stop() = Unit

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val LABEL = "LOCAL ANDROID TTS DISABLED"
    }
}
