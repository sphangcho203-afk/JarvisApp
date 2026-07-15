package com.seongja.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Always-available Android speech output for Jarvis.
 *
 * The premium PCM socket remains optional. When it is absent, Jarvis speaks
 * through the best installed offline English voice instead of becoming mute.
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

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val ready = AtomicBoolean(false)
    private var engine: TextToSpeech? = null
    private var destroyed = false
    private var pendingSpeech: String? = null
    private var activeUtteranceId: String? = null
    private var voiceLabel = "ANDROID LOCAL VOICE"

    init {
        engine = TextToSpeech(appContext) { status ->
            handler.post {
                if (destroyed) return@post
                val tts = engine
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    ready.set(false)
                    listener.onError("Android text-to-speech initialization failed.")
                    return@post
                }

                configure(tts)
                ready.set(true)
                listener.onReady(voiceLabel)
                pendingSpeech?.let { queued ->
                    pendingSpeech = null
                    speak(queued)
                }
            }
        }
    }

    fun isReady(): Boolean = ready.get() && !destroyed

    fun label(): String = voiceLabel

    fun speak(text: String): Boolean {
        val clean = JarvisResponseSanitizer.spoken(text).trim()
        if (clean.isBlank() || destroyed) return false

        val tts = engine
        if (!isReady() || tts == null) {
            pendingSpeech = clean
            handler.postDelayed({
                if (!destroyed && pendingSpeech == clean && !isReady()) {
                    pendingSpeech = null
                    listener.onError("Android voice engine did not become ready.")
                }
            }, STARTUP_GRACE_MS)
            return true
        }

        val utteranceId = "jarvis-${UUID.randomUUID()}"
        activeUtteranceId = utteranceId
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val result = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            activeUtteranceId = null
            listener.onError("Android voice engine rejected speech output ($result).")
            return false
        }
        return true
    }

    fun stop() {
        pendingSpeech = null
        activeUtteranceId = null
        runCatching { engine?.stop() }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pendingSpeech = null
        activeUtteranceId = null
        handler.removeCallbacksAndMessages(null)
        ready.set(false)
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }

    private fun configure(tts: TextToSpeech) {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        val selected = selectVoice(tts.voices.orEmpty())
        if (selected != null) {
            tts.voice = selected
            voiceLabel = "ANDROID LOCAL // ${selected.name.uppercase(Locale.US).take(42)}"
        } else {
            tts.language = Locale.UK
            voiceLabel = "ANDROID LOCAL // ENGLISH"
        }

        // Slightly lower and measured, avoiding the default navigation-assistant cadence.
        tts.setPitch(0.84f)
        tts.setSpeechRate(0.91f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    handler.post { listener.onStarted(voiceLabel) }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != activeUtteranceId) return
                activeUtteranceId = null
                handler.post(listener::onCompleted)
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                onError(utteranceId, TextToSpeech.ERROR)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != activeUtteranceId) return
                activeUtteranceId = null
                handler.post {
                    listener.onError("Android voice playback failed ($errorCode).")
                }
            }
        })
    }

    private fun selectVoice(voices: Set<Voice>): Voice? {
        val english = voices.filter {
            it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) &&
                !it.isNetworkConnectionRequired
        }
        if (english.isEmpty()) return null

        return english.maxWithOrNull(
            compareBy<Voice> { voicePreference(it) }
                .thenBy { it.quality }
                .thenByDescending { -it.latency }
        )
    }

    private fun voicePreference(voice: Voice): Int {
        val name = voice.name.lowercase(Locale.US)
        var score = 0
        if (voice.locale.country.equals("GB", ignoreCase = true)) score += 24
        if (voice.locale.country.equals("US", ignoreCase = true)) score += 16
        if ("local" in name) score += 12
        if ("male" in name || "deep" in name || "baritone" in name) score += 18
        if ("female" in name || "child" in name) score -= 8
        score += voice.quality
        score -= voice.latency
        return score
    }

    companion object {
        private const val STARTUP_GRACE_MS = 5_000L
    }
}
