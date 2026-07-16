package com.seongja.jarvis

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local operational status fabric shared by the reasoning layer and HELIX.
 * It carries only short, non-secret labels. API keys and response bodies never enter it.
 */
data class JarvisOperationSignal(
    val stage: String,
    val detail: String,
    val progress: Float,
    val active: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

object JarvisOperationBus {
    private val listeners = CopyOnWriteArraySet<(JarvisOperationSignal) -> Unit>()

    fun addListener(listener: (JarvisOperationSignal) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (JarvisOperationSignal) -> Unit) {
        listeners -= listener
    }

    fun publish(
        stage: String,
        detail: String,
        progress: Float = 0f,
        active: Boolean = true
    ) {
        val signal = JarvisOperationSignal(
            stage = stage.trim().uppercase().take(48),
            detail = detail.trim().replace(Regex("\\s+"), " ").take(180),
            progress = progress.coerceIn(0f, 1f),
            active = active
        )
        listeners.forEach { listener -> runCatching { listener(signal) } }
    }

    fun clear(detail: String = "OPERATION COMPLETE") {
        publish(
            stage = "SYSTEM READY",
            detail = detail,
            progress = 1f,
            active = false
        )
    }
}
