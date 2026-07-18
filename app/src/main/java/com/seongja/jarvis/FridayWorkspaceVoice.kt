package com.seongja.jarvis

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Cartesia output for dedicated native workspaces such as X-Camera.
 * It does not open a microphone or create a second recognition loop.
 */
class FridayWorkspaceVoice(
    context: Context,
    private val onDiagnostic: (String) -> Unit = {},
    private val onComplete: () -> Unit = {}
) : CartesiaSonicClient.Listener {
    private val destroyed = AtomicBoolean(false)
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
        onDiagnostic("WORKSPACE VOICE -> $label READY")
    }

    override fun onAudioStarted(label: String) {
        onDiagnostic("WORKSPACE VOICE -> $label SPEAKING")
    }

    override fun onCompleted() {
        onDiagnostic("WORKSPACE VOICE -> COMPLETE")
        onComplete()
    }

    override fun onDiagnostic(message: String) {
        onDiagnostic.invoke(message)
    }

    override fun onError(message: String) {
        onDiagnostic("WORKSPACE VOICE -> ERROR // $message")
        onComplete()
    }
}
