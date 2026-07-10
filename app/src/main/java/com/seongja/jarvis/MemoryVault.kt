package com.seongja.jarvis

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryVault(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_phase5_memory", Context.MODE_PRIVATE)
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit()
                .putBoolean(KEY_SEEDED, true)
                .putString(KEY_CALLSIGN, "Seongja")
                .putString(KEY_PROFILE, "Operator: Seongja // Role: Jarvis creator // Mission: Build a phone-first offline AI assistant.")
                .putString(KEY_FACTS, listOf(
                    stamped("Jarvis local memory initialized."),
                    stamped("Default identity: Seongja, creator/operator of Jarvis."),
                    stamped("Design directive: advanced civilization, geometric, live HUD.")
                ).joinToString("\n"))
                .apply()
        }
    }

    fun callsign(): String = prefs.getString(KEY_CALLSIGN, "Seongja") ?: "Seongja"

    fun profile(): String = prefs.getString(KEY_PROFILE, "Operator profile not configured.")
        ?: "Operator profile not configured."

    fun setIdentity(name: String) {
        val clean = name.trim().ifBlank { "Seongja" }.take(40)
        prefs.edit()
            .putString(KEY_CALLSIGN, clean)
            .putString(KEY_PROFILE, "Operator: $clean // Role: Jarvis operator // Mission: Build and command Jarvis.")
            .apply()
        addFact("Operator identity updated to $clean.")
    }

    fun addFact(fact: String) {
        val clean = fact.trim().take(220)
        if (clean.isBlank()) return
        val old = prefs.getString(KEY_FACTS, "") ?: ""
        val line = stamped(clean)
        val merged = (line + "\n" + old).lines().filter { it.isNotBlank() }.take(60).joinToString("\n")
        prefs.edit().putString(KEY_FACTS, merged).apply()
    }

    fun addHistory(entry: String) {
        val clean = entry.trim().take(220)
        if (clean.isBlank()) return
        val old = prefs.getString(KEY_HISTORY, "") ?: ""
        val line = stamped(clean)
        val merged = (line + "\n" + old).lines().filter { it.isNotBlank() }.take(30).joinToString("\n")
        prefs.edit().putString(KEY_HISTORY, merged).apply()
    }

    fun clearUserFactsKeepIdentity() {
        prefs.edit()
            .putString(KEY_FACTS, stamped("Memory cleared. Core identity retained: ${callsign()}."))
            .putString(KEY_HISTORY, "")
            .apply()
    }

    fun summary(): String = "OPERATOR ${callsign()} // FACTS ${facts().size} // HISTORY ${history().size}"

    fun promptContext(): String = buildString {
        appendLine(profile())
        appendLine("Stored facts:")
        facts().take(12).forEach { appendLine("- $it") }
        appendLine("Recent conversation:")
        history().take(8).reversed().forEach { appendLine("- $it") }
    }.trim()

    fun expanded(): String = promptContext()

    fun facts(): List<String> = (prefs.getString(KEY_FACTS, "") ?: "")
        .lines().filter { it.isNotBlank() }

    fun history(): List<String> = (prefs.getString(KEY_HISTORY, "") ?: "")
        .lines().filter { it.isNotBlank() }

    private fun stamped(text: String): String = "${format.format(Date())} // $text"

    companion object {
        private const val KEY_SEEDED = "seeded"
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_PROFILE = "profile"
        private const val KEY_FACTS = "facts"
        private const val KEY_HISTORY = "history"
    }
}
