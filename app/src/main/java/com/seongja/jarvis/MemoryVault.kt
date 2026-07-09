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
                .putString(KEY_PROFILE, "Operator: Seongja // Role: Jarvis creator // Mission: Build a phone-first AI assistant with advanced civilization interface.")
                .putString(KEY_FACTS, listOf(
                    stamped("Jarvis Phase 5 local brain initialized."),
                    stamped("Default identity loaded: Seongja, creator/operator of Jarvis."),
                    stamped("Design directive: advanced civilization, sci-fi, geometric, live HUD.")
                ).joinToString("\n"))
                .apply()
        }
    }

    fun callsign(): String = prefs.getString(KEY_CALLSIGN, "Seongja") ?: "Seongja"

    fun profile(): String = prefs.getString(KEY_PROFILE, "Operator profile not configured.") ?: "Operator profile not configured."

    fun setIdentity(name: String) {
        val clean = name.trim().ifBlank { "Seongja" }.take(40)
        prefs.edit()
            .putString(KEY_CALLSIGN, clean)
            .putString(KEY_PROFILE, "Operator: $clean // Role: Jarvis operator // Mission: Build and command Jarvis.")
            .apply()
        addFact("Operator identity updated to $clean.")
    }

    fun addFact(fact: String) {
        val old = prefs.getString(KEY_FACTS, "") ?: ""
        val line = stamped(fact.trim().take(220))
        val merged = (line + "\n" + old).lines().filter { it.isNotBlank() }.take(40).joinToString("\n")
        prefs.edit().putString(KEY_FACTS, merged).apply()
    }

    fun addHistory(entry: String) {
        val old = prefs.getString(KEY_HISTORY, "") ?: ""
        val line = stamped(entry.trim().take(160))
        val merged = (line + "\n" + old).lines().filter { it.isNotBlank() }.take(20).joinToString("\n")
        prefs.edit().putString(KEY_HISTORY, merged).apply()
    }

    fun clearUserFactsKeepIdentity() {
        prefs.edit()
            .putString(KEY_FACTS, stamped("Memory vault cleared. Core identity retained: ${callsign()}."))
            .putString(KEY_HISTORY, "")
            .apply()
    }

    fun summary(): String {
        val factCount = facts().size
        val histCount = history().size
        return "OPERATOR ${callsign()} // FACTS $factCount // HISTORY $histCount"
    }

    fun expanded(): String = buildString {
        appendLine(profile())
        appendLine()
        appendLine("RECENT FACTS:")
        val facts = facts().take(8)
        if (facts.isEmpty()) appendLine("No stored facts.") else facts.forEach { appendLine(it) }
        appendLine()
        appendLine("RECENT CONTEXT:")
        val history = history().take(5)
        if (history.isEmpty()) append("No recent context.") else history.forEach { appendLine(it) }
    }.trim()

    fun facts(): List<String> = (prefs.getString(KEY_FACTS, "") ?: "").lines().filter { it.isNotBlank() }

    fun history(): List<String> = (prefs.getString(KEY_HISTORY, "") ?: "").lines().filter { it.isNotBlank() }

    private fun stamped(text: String): String = "${format.format(Date())} // $text"

    companion object {
        private const val KEY_SEEDED = "seeded"
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_PROFILE = "profile"
        private const val KEY_FACTS = "facts"
        private const val KEY_HISTORY = "history"
    }
}
