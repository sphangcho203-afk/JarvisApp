package com.seongja.jarvis

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryVault(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_memory_vault", Context.MODE_PRIVATE)
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun callsign(): String = prefs.getString(KEY_CALLSIGN, "Sir") ?: "Sir"

    fun setCallsign(value: String) {
        prefs.edit().putString(KEY_CALLSIGN, value.trim().take(32)).apply()
        addNote("Callsign updated to ${value.trim().take(32)}")
    }

    fun addNote(note: String) {
        val old = prefs.getString(KEY_NOTES, "") ?: ""
        val line = "${format.format(Date())} // ${note.trim().take(180)}"
        val merged = (line + "\n" + old).lines().take(14).joinToString("\n")
        prefs.edit().putString(KEY_NOTES, merged).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun summary(): String {
        val notes = prefs.getString(KEY_NOTES, "") ?: ""
        return "CALLSIGN ${callsign()} // NOTES ${notes.lines().filter { it.isNotBlank() }.size}"
    }

    fun expanded(): String {
        val notes = prefs.getString(KEY_NOTES, "") ?: ""
        return buildString {
            appendLine("CALLSIGN: ${callsign()}")
            appendLine("RECENT MEMORY:")
            if (notes.isBlank()) append("No stored notes yet.") else append(notes)
        }
    }

    companion object {
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_NOTES = "notes"
    }
}
