package com.seongja.jarvis

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import java.util.Locale

class JarvisBrain(private val context: Context) {
    private val memory = MemoryVault(context)
    private var cognitionCycles = 0

    fun respond(rawInput: String): BrainResponse {
        cognitionCycles++
        val input = rawInput.trim()
        val lower = input.lowercase(Locale.US)
        val trace = mutableListOf("input_normalized", "semantic_router", "memory_context_loaded")

        return when {
            lower.contains("who am i") || lower.contains("who i am") || lower.contains("who am l") -> {
                trace += "identity_query"
                response(
                    spoken = "You are ${memory.callsign()}. Local memory is online, and the cognitive shell recognizes your profile.",
                    display = memory.expanded(),
                    intent = "identity_query",
                    confidence = 0.98f,
                    mode = BrainMode.THINKING,
                    trace = trace
                )
            }

            lower.startsWith("call me ") -> {
                val name = input.substringAfter("call me", "").trim().ifBlank { "Sir" }
                memory.setCallsign(name)
                trace += "memory_write_callsign"
                response(
                    spoken = "Acknowledged. Callsign updated to $name.",
                    display = "Identity marker stored. Callsign: $name",
                    intent = "memory_set_callsign",
                    confidence = 0.99f,
                    mode = BrainMode.ONLINE,
                    trace = trace
                )
            }

            lower.startsWith("my name is ") -> {
                val name = input.substringAfter("my name is", "").trim().ifBlank { "Sir" }
                memory.setCallsign(name)
                trace += "memory_write_identity"
                response(
                    spoken = "Identity updated. I will address you as $name.",
                    display = "Identity updated -> $name",
                    intent = "memory_set_identity",
                    confidence = 0.99f,
                    mode = BrainMode.ONLINE,
                    trace = trace
                )
            }

            lower.startsWith("remember ") || lower.startsWith("remember that ") -> {
                val note = input.removePrefix("remember that").removePrefix("remember").trim()
                if (note.isNotBlank()) memory.addNote(note)
                trace += "memory_write_note"
                response(
                    spoken = if (note.isBlank()) "No memory payload detected." else "Memory stored.",
                    display = if (note.isBlank()) "No memory payload detected." else "Stored memory node: $note",
                    intent = "memory_store",
                    confidence = if (note.isBlank()) 0.4f else 0.97f,
                    mode = BrainMode.THINKING,
                    trace = trace
                )
            }

            lower.contains("what do you remember") || lower.contains("memory vault") -> {
                trace += "memory_read"
                response(
                    spoken = "Memory vault loaded.",
                    display = memory.expanded(),
                    intent = "memory_read",
                    confidence = 0.96f,
                    mode = BrainMode.THINKING,
                    trace = trace
                )
            }

            lower.contains("clear memory") || lower.contains("wipe memory") -> {
                memory.clear()
                trace += "memory_wipe"
                response(
                    spoken = "Local memory vault cleared.",
                    display = "Memory vault cleared. Identity and notes reset.",
                    intent = "memory_clear",
                    confidence = 0.99f,
                    mode = BrainMode.SECURITY,
                    trace = trace
                )
            }

            lower.contains("battery") || lower.contains("system status") || lower.contains("diagnostic") -> {
                trace += "diagnostics"
                val battery = batteryPercent()
                val status = "BATTERY $battery% // MODEL ${Build.MODEL} // SDK ${Build.VERSION.SDK_INT} // CYCLES $cognitionCycles"
                response(
                    spoken = "Diagnostics online. Battery is at $battery percent.",
                    display = status,
                    intent = "diagnostics",
                    confidence = 0.93f,
                    mode = BrainMode.TACTICAL,
                    trace = trace
                )
            }

            lower.contains("stealth") -> {
                trace += "mode_stealth"
                response(
                    spoken = "Stealth interface engaged.",
                    display = "MODE SHIFT -> STEALTH. Visual energy reduced. Passive cognition maintained.",
                    intent = "mode_stealth",
                    confidence = 0.93f,
                    mode = BrainMode.STEALTH,
                    trace = trace
                )
            }

            lower.contains("tactical") || lower.contains("combat") -> {
                trace += "mode_tactical"
                response(
                    spoken = "Tactical cognition engaged.",
                    display = "MODE SHIFT -> TACTICAL. Command density increased. Sensor panels active.",
                    intent = "mode_tactical",
                    confidence = 0.94f,
                    mode = BrainMode.TACTICAL,
                    trace = trace
                )
            }

            lower.contains("red alert") || lower.contains("alert mode") -> {
                trace += "mode_alert"
                response(
                    spoken = "Alert mode online.",
                    display = "MODE SHIFT -> ALERT. High-contrast warning layer armed.",
                    intent = "mode_alert",
                    confidence = 0.94f,
                    mode = BrainMode.ALERT,
                    trace = trace
                )
            }

            looksLikeQuestion(lower) -> {
                trace += "offline_reasoning"
                val answer = offlineSynthesis(input)
                response(
                    spoken = answer.take(220),
                    display = answer,
                    intent = "knowledge_synthesis_offline",
                    confidence = 0.68f,
                    mode = BrainMode.THINKING,
                    trace = trace
                )
            }

            else -> {
                trace += "general_cognition"
                response(
                    spoken = "I understand the instruction, but the full cloud intelligence layer is not connected yet.",
                    display = "Parsed meaning: ${input.take(120)}\n\nLocal cognition active. Cloud brain connector is architected next: API gateway, retrieval memory, web knowledge, and tool execution.",
                    intent = "general_understanding",
                    confidence = 0.74f,
                    mode = BrainMode.THINKING,
                    trace = trace
                )
            }
        }
    }

    fun memorySnapshot(): String = memory.summary()

    private fun response(
        spoken: String,
        display: String,
        intent: String,
        confidence: Float,
        mode: BrainMode,
        trace: List<String>
    ): BrainResponse = BrainResponse(
        spoken = spoken,
        display = display,
        intent = intent,
        confidence = confidence.coerceIn(0f, 1f),
        mode = mode,
        trace = trace + "response_rendered",
        memory = memory.summary()
    )

    private fun looksLikeQuestion(lower: String): Boolean {
        val starters = listOf("what", "why", "how", "when", "where", "who", "explain", "tell me")
        return lower.endsWith("?") || starters.any { lower.startsWith(it) }
    }

    private fun offlineSynthesis(input: String): String {
        return "Offline cognition engaged for: \"$input\". I can break it into intent, context, likely meaning, and next action. For real world knowledge beyond the app, the next brain layer needs a connected model API plus retrieval memory. No magic omniscience, sadly. Physics remains rude."
    }

    private fun batteryPercent(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: -1
    }
}
