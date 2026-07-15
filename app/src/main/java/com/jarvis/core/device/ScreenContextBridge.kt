package com.jarvis.core.device

import android.os.SystemClock

/**
 * Short-lived in-memory handoff for the screen that was visible immediately
 * before Jarvis was summoned. No snapshot is written to disk or conversation
 * memory.
 */
object ScreenContextBridge {
    private data class Entry(
        val snapshot: ScreenContextSnapshot,
        val capturedAtElapsedMs: Long
    )

    @Volatile
    private var latest: Entry? = null

    fun capture(snapshot: ScreenContextSnapshot?) {
        if (snapshot == null) return
        if (snapshot.packageName == JARVIS_PACKAGE) return
        latest = Entry(snapshot, SystemClock.elapsedRealtime())
    }

    fun recent(maxAgeMs: Long = DEFAULT_TTL_MS): ScreenContextSnapshot? {
        val entry = latest ?: return null
        if (SystemClock.elapsedRealtime() - entry.capturedAtElapsedMs > maxAgeMs) {
            latest = null
            return null
        }
        return entry.snapshot
    }

    fun clear() {
        latest = null
    }

    private const val JARVIS_PACKAGE = "com.seongja.jarvis"
    private const val DEFAULT_TTL_MS = 90_000L
}
