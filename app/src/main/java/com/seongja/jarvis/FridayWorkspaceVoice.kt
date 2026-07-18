package com.seongja.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Voice output for dedicated native workspaces such as X-Camera.
 * Cartesia remains the preferred route; Android TTS is an honest local fallback.
 * This class never opens a microphone or creates a recognition loop.
 */
class FridayWorkspaceVoice(
    context: Context,
    onDiagnostic: (String) -> Unit = {},
    onComplete: () -> Unit = {}
) : CartesiaSonicClient.Listener {
    private val destroyed = AtomicBoolean(false)
    private val localReady = AtomicBoolean(false)
    private val diagnosticSink = onDiagnostic
    private val completionSink = onComplete
    private val client = CartesiaSonicClient(context.applicationContext, this)
    private val localTts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            localTts.language = Locale.UK
            localTts.setSpeechRate(1.06f)
            localTts.setPitch(1.0f)
            localReady.set(true)
            diagnosticSink("WORKSPACE VOICE -> ANDROID TTS READY")
        } else {
            localReady.set(false)
            diagnosticSink("WORKSPACE VOICE -> ANDROID TTS UNAVAILABLE")
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                diagnosticSink("WORKSPACE VOICE -> ANDROID TTS SPEAKING")
            }

            override fun onDone(utteranceId: String?) {
                diagnosticSink("WORKSPACE VOICE -> ANDROID TTS COMPLETE")
                completionSink()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                diagnosticSink("WORKSPACE VOICE -> ANDROID TTS ERROR")
                completionSink()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                diagnosticSink("WORKSPACE VOICE -> ANDROID TTS ERROR // $errorCode")
                completionSink()
            }
        })
    }

    fun isConfigured(): Boolean = client.isConfigured() || localReady.get()

    fun speak(text: String): Boolean {
        if (destroyed.get()) return false
        val clean = JarvisResponseSanitizer.spoken(text)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1_400)
        if (clean.isBlank()) return false

        if (client.begin()) {
            client.push(clean)
            client.finish()
            return true
        }

        if (!localReady.get()) {
            diagnosticSink("WORKSPACE VOICE -> NO READY OUTPUT ROUTE")
            return false
        }

        val utteranceId = "friday-workspace-${UUID.randomUUID()}"
        return localTts.speak(
            clean,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        ) == TextToSpeech.SUCCESS
    }

    fun stop() {
        if (destroyed.get()) return
        client.cancel()
        localTts.stop()
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        client.destroy()
        localTts.stop()
        localTts.shutdown()
    }

    override fun onReady(label: String) {
        diagnosticSink("WORKSPACE VOICE -> $label READY")
    }

    override fun onAudioStarted(label: String) {
        diagnosticSink("WORKSPACE VOICE -> $label SPEAKING")
    }

    override fun onCompleted() {
        diagnosticSink("WORKSPACE VOICE -> COMPLETE")
        completionSink()
    }

    override fun onDiagnostic(message: String) {
        diagnosticSink(message)
    }

    override fun onError(message: String) {
        diagnosticSink("WORKSPACE VOICE -> ERROR // $message")
        completionSink()
    }
}
