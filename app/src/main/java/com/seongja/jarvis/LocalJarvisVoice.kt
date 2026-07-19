package com.seongja.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local speech output selected by the owner inside FRIDAY Voice Lab.
 *
 * The chosen engine, voice, pitch and rate stay on-device in SharedPreferences.
 * Cartesia and the premium PCM gateway can still take priority, while this class
 * supplies a real local fallback instead of silently dropping speech output.
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
    private val initializationGeneration = AtomicInteger(0)
    private val utteranceCounter = AtomicInteger(0)

    private var tts: TextToSpeech? = null
    private var destroyed = false
    private var ready = false
    private var profile = FridayVoiceStore.load(appContext)
    private var activeEnginePackage = profile.enginePackage
    private var activeLabel = "FRIDAY VOICE LAB // INITIALIZING"

    init {
        initialize(profile)
    }

    fun isReady(): Boolean = ready && !destroyed

    fun label(): String = activeLabel

    fun reloadProfile() {
        if (destroyed) return
        val latest = FridayVoiceStore.load(appContext)
        val engineChanged = latest.enginePackage != activeEnginePackage
        profile = latest
        if (engineChanged || tts == null) {
            initialize(latest)
        } else {
            tts?.let { applyProfile(it, latest, announce = true) }
        }
    }

    fun speak(text: String): Boolean {
        if (destroyed) return false
        val clean = JarvisResponseSanitizer.spoken(text)
        if (clean.isBlank()) return false

        val engine = tts
        if (!ready || engine == null) {
            handler.post {
                if (!destroyed) listener.onError("FRIDAY Voice Lab is not ready yet.")
            }
            return false
        }

        val latest = FridayVoiceStore.load(appContext)
        if (latest.enginePackage != activeEnginePackage) {
            profile = latest
            initialize(latest)
            handler.post {
                if (!destroyed) listener.onError("FRIDAY voice engine changed and is reinitializing.")
            }
            return false
        }
        profile = latest
        applyProfile(engine, latest, announce = false)

        val utteranceId = "friday-local-${utteranceCounter.incrementAndGet()}"
        val result = engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            handler.post {
                if (!destroyed) listener.onError("The selected local speech engine rejected the request.")
            }
            return false
        }
        return true
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        destroyed = true
        ready = false
        initializationGeneration.incrementAndGet()
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun initialize(requestedProfile: FridayVoiceProfile) {
        if (destroyed) return
        val generation = initializationGeneration.incrementAndGet()
        ready = false
        profile = requestedProfile
        activeEnginePackage = requestedProfile.enginePackage
        activeLabel = "FRIDAY VOICE LAB // INITIALIZING"

        tts?.stop()
        tts?.shutdown()
        tts = null

        val onInit = TextToSpeech.OnInitListener { result ->
            handler.post {
                if (destroyed || generation != initializationGeneration.get()) return@post
                val engine = tts
                if (result != TextToSpeech.SUCCESS || engine == null) {
                    ready = false
                    activeLabel = "FRIDAY VOICE LAB // ENGINE UNAVAILABLE"
                    listener.onError("No working Android text-to-speech engine is available.")
                    return@post
                }

                activeEnginePackage = engine.defaultEngine.orEmpty()
                    .ifBlank { requestedProfile.enginePackage }
                installProgressListener(engine)
                applyProfile(engine, requestedProfile, announce = true)
            }
        }

        tts = if (requestedProfile.enginePackage.isBlank()) {
            TextToSpeech(appContext, onInit)
        } else {
            TextToSpeech(appContext, onInit, requestedProfile.enginePackage)
        }
    }

    private fun installProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.post {
                    if (!destroyed) listener.onStarted(activeLabel)
                }
            }

            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (!destroyed) listener.onCompleted()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (!destroyed) listener.onError("Local speech synthesis failed.")
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handler.post {
                    if (!destroyed) listener.onError("Local speech synthesis failed with code $errorCode.")
                }
            }
        })
    }

    private fun applyProfile(
        engine: TextToSpeech,
        selectedProfile: FridayVoiceProfile,
        announce: Boolean
    ) {
        val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val selected = FridayVoiceSelector.select(voices, selectedProfile)

        val voiceApplied = selected?.let { engine.setVoice(it) != TextToSpeech.ERROR } == true
        if (!voiceApplied) {
            val locale = Locale.forLanguageTag(selectedProfile.localeTag)
                .takeIf { it.language.isNotBlank() }
                ?: Locale.US
            engine.setLanguage(locale)
        }
        engine.setSpeechRate(selectedProfile.speechRate.coerceIn(0.70f, 1.30f))
        engine.setPitch(selectedProfile.pitch.coerceIn(0.80f, 1.20f))

        val activeVoice = selected?.takeIf { voiceApplied } ?: engine.voice
        val route = if (activeVoice?.isNetworkConnectionRequired == true) "NETWORK" else "LOCAL"
        val voiceName = activeVoice?.name.orEmpty().ifBlank { "SYSTEM DEFAULT" }
        activeLabel = "FRIDAY VOICE LAB // $route // ${voiceName.take(48)}"
        ready = true
        if (announce) listener.onReady(activeLabel)
    }
}
