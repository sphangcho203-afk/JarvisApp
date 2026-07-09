package com.seongja.jarvis

import android.content.Context

class JarvisPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_core", Context.MODE_PRIVATE)

    var callsign: String
        get() = prefs.getString(KEY_CALLSIGN, "Sir") ?: "Sir"
        set(value) {
            prefs.edit().putString(KEY_CALLSIGN, value.trim().ifBlank { "Sir" }).apply()
        }

    var commandCount: Int
        get() = prefs.getInt(KEY_COMMAND_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_COMMAND_COUNT, value.coerceAtLeast(0)).apply()
        }

    var lastCommand: String
        get() = prefs.getString(KEY_LAST_COMMAND, "NONE") ?: "NONE"
        set(value) {
            prefs.edit().putString(KEY_LAST_COMMAND, value.take(80)).apply()
        }

    var lastBootMillis: Long
        get() = prefs.getLong(KEY_LAST_BOOT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_BOOT, value).apply()
        }

    fun nextCommandCount(command: String): Int {
        val next = commandCount + 1
        commandCount = next
        lastCommand = command
        return next
    }

    fun clearSoftMemory() {
        prefs.edit()
            .remove(KEY_CALLSIGN)
            .remove(KEY_LAST_COMMAND)
            .apply()
    }

    companion object {
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_COMMAND_COUNT = "command_count"
        private const val KEY_LAST_COMMAND = "last_command"
        private const val KEY_LAST_BOOT = "last_boot_millis"
    }
}
