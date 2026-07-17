package com.seongja.jarvis

import android.content.Context
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

enum class ScreenVisibilityState {
    NONE,
    ACCESSIBILITY_ONLY,
    SCREEN_CAPTURE_ACTIVE,
    SCREEN_CAPTURE_AND_ACCESSIBILITY,
    ERROR
}

data class ScreenVisionSnapshot(
    val state: ScreenVisibilityState = ScreenVisibilityState.NONE,
    val sessionStartedAtMs: Long = 0L,
    val lastFrameAtMs: Long = 0L,
    val lastError: String = ""
) {
    fun truthfulAnswer(): String = when (state) {
        ScreenVisibilityState.SCREEN_CAPTURE_AND_ACCESSIBILITY ->
            "Yes, Sir. Screen capture and supported interface controls are both visible."
        ScreenVisibilityState.SCREEN_CAPTURE_ACTIVE ->
            "Yes, Sir. The visual screen channel is active. Supported interface controls may be limited."
        ScreenVisibilityState.ACCESSIBILITY_ONLY ->
            "Partially, Sir. I can read supported text and controls, but visual screen capture is offline."
        ScreenVisibilityState.ERROR ->
            "No, Sir. The visual channel failed: ${lastError.ifBlank { "unknown capture error" }.take(180)}"
        ScreenVisibilityState.NONE ->
            "No, Sir. I currently have no screen visibility."
    }
}

object ScreenVisionRuntime {
    private val listeners = CopyOnWriteArrayList<(ScreenVisionSnapshot) -> Unit>()
    @Volatile private var snapshot = ScreenVisionSnapshot()
    @Volatile private var latestFrameJpeg: ByteArray? = null

    fun snapshot(): ScreenVisionSnapshot = snapshot

    fun addListener(listener: (ScreenVisionSnapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    fun removeListener(listener: (ScreenVisionSnapshot) -> Unit) {
        listeners -= listener
    }

    fun refreshAccessibility(context: Context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val accessibility = manager?.isEnabled == true
        val capture = snapshot.state == ScreenVisibilityState.SCREEN_CAPTURE_ACTIVE ||
            snapshot.state == ScreenVisibilityState.SCREEN_CAPTURE_AND_ACCESSIBILITY
        setState(
            when {
                capture && accessibility -> ScreenVisibilityState.SCREEN_CAPTURE_AND_ACCESSIBILITY
                capture -> ScreenVisibilityState.SCREEN_CAPTURE_ACTIVE
                accessibility -> ScreenVisibilityState.ACCESSIBILITY_ONLY
                else -> ScreenVisibilityState.NONE
            },
            preserveSession = capture
        )
    }

    fun markCaptureStarted(context: Context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val accessibility = manager?.isEnabled == true
        snapshot = ScreenVisionSnapshot(
            state = if (accessibility) {
                ScreenVisibilityState.SCREEN_CAPTURE_AND_ACCESSIBILITY
            } else {
                ScreenVisibilityState.SCREEN_CAPTURE_ACTIVE
            },
            sessionStartedAtMs = System.currentTimeMillis()
        )
        publish()
    }

    fun updateFrame(jpeg: ByteArray) {
        latestFrameJpeg = jpeg.copyOf()
        snapshot = snapshot.copy(lastFrameAtMs = System.currentTimeMillis())
        publish()
    }

    fun consumeLatestFrame(): ByteArray? = latestFrameJpeg?.copyOf()

    fun markError(message: String) {
        latestFrameJpeg?.fill(0)
        latestFrameJpeg = null
        snapshot = ScreenVisionSnapshot(
            state = ScreenVisibilityState.ERROR,
            lastError = message.replace(Regex("\\s+"), " ").trim().take(240)
        )
        publish()
    }

    fun clear(context: Context? = null) {
        latestFrameJpeg?.fill(0)
        latestFrameJpeg = null
        snapshot = ScreenVisionSnapshot()
        if (context != null) refreshAccessibility(context)
        else publish()
    }

    private fun setState(state: ScreenVisibilityState, preserveSession: Boolean) {
        snapshot = snapshot.copy(
            state = state,
            sessionStartedAtMs = if (preserveSession) snapshot.sessionStartedAtMs else 0L,
            lastFrameAtMs = if (preserveSession) snapshot.lastFrameAtMs else 0L,
            lastError = ""
        )
        publish()
    }

    private fun publish() {
        val current = snapshot
        listeners.forEach { listener -> runCatching { listener(current) } }
    }
}

object OpticalVisionRuntime {
    private var active: WeakReference<OpticalVisionActivity>? = null

    @Synchronized
    fun attach(activity: OpticalVisionActivity) {
        active = WeakReference(activity)
    }

    @Synchronized
    fun detach(activity: OpticalVisionActivity) {
        if (active?.get() === activity) active = null
    }

    @Synchronized
    fun closeActive(reason: String): Boolean {
        val activity = active?.get() ?: return false
        activity.runOnUiThread { activity.closeVision(reason) }
        return true
    }
}
