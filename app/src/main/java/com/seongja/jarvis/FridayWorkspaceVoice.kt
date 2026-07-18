package com.seongja.jarvis

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Cartesia output for dedicated native workspaces such as X-Camera.
 * It does not open a microphone or create a second recognition loop.
 */
class FridayWorkspaceVoice(
    context: Context,
    onDiagnostic: (String) -> Unit = {},
    onComplete: () -> Unit = {}
) : CartesiaSonicClient.Listener {
    private val destroyed = AtomicBoolean(false)
    private val diagnosticSink = onDiagnostic
    private val completionSink = onComplete
    private val client = CartesiaSonicClient(context.applicationContext, this)

    fun isConfigured(): Boolean = client.isConfigured()

    fun speak(text: String): Boolean {
        if (destroyed.get()) return false
        val clean = JarvisResponseSanitizer.spoken(text)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1_400)
        if (clean.isBlank() || !client.begin()) return false
        client.push(clean)
        client.finish()
        return true
    }

    fun stop() {
        if (!destroyed.get()) client.cancel()
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        client.destroy()
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
