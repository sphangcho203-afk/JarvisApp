package com.seongja.jarvis

import android.content.Context
import java.util.Locale

enum class FridayDesignMode(val id: String, val spokenLabel: String) {
    STANDARD("STANDARD", "standard interface"),
    FOCUS("FOCUS", "focus interface"),
    LAB("LAB", "laboratory interface"),
    STEALTH("STEALTH", "stealth interface"),
    SECURE("SECURE", "secure interface"),
    ENERGY("ENERGY", "energy-saving interface"),
    QUANTUM("QUANTUM", "quantum interface");

    fun next(): FridayDesignMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromId(value: String?): FridayDesignMode =
            entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: STANDARD
    }
}

sealed interface FridayDesignCommand {
    data object Cycle : FridayDesignCommand
    data class Activate(val mode: FridayDesignMode) : FridayDesignCommand
}

object FridayDesignCommandParser {
    fun parse(input: String): FridayDesignCommand? {
        val normalized = input
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return null

        val mentionsInterface = listOf(
            "design", "interface", "theme", "visual mode", "system look", "display style"
        ).any(normalized::contains)
        val activationIntent = listOf(
            "change", "switch", "activate", "use", "set", "open", "enable"
        ).any(normalized::contains)
        if (!mentionsInterface || !activationIntent) return null

        val selected = when {
            "quantum" in normalized -> FridayDesignMode.QUANTUM
            "stealth" in normalized -> FridayDesignMode.STEALTH
            "secure" in normalized || "privacy" in normalized -> FridayDesignMode.SECURE
            "laboratory" in normalized || Regex("\\blab\\b").containsMatchIn(normalized) -> FridayDesignMode.LAB
            "focus" in normalized -> FridayDesignMode.FOCUS
            "energy" in normalized || "battery" in normalized || "low power" in normalized -> FridayDesignMode.ENERGY
            "standard" in normalized || "default" in normalized || "normal" in normalized -> FridayDesignMode.STANDARD
            else -> null
        }
        return selected?.let(FridayDesignCommand::Activate) ?: FridayDesignCommand.Cycle
    }
}

class FridayDesignModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): FridayDesignMode = FridayDesignMode.fromId(preferences.getString(KEY_MODE, null))

    fun save(mode: FridayDesignMode) {
        preferences.edit().putString(KEY_MODE, mode.id).apply()
    }

    companion object {
        private const val PREFS = "friday_design_matrix"
        private const val KEY_MODE = "active_mode"
    }
}
