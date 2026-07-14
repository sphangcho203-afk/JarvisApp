package com.seongja.jarvis

/**
 * Central runtime policy for dialogue generation and provider failover.
 *
 * Conversation is intentionally more expressive than coding and verification
 * work. Provider safety behavior remains at the official API defaults; this
 * client does not attempt to disable built-in protections.
 */
object JarvisRuntimeConfig {
    const val CONVERSATION_TEMPERATURE = 0.85
    const val FAST_TEMPERATURE = 0.72
    const val REASONING_TEMPERATURE = 0.35
    const val CODING_TEMPERATURE = 0.20
    const val DIAGNOSTIC_TEMPERATURE = 0.0

    const val MAX_NETWORK_ATTEMPTS = 8
    const val MAX_MODELS_PER_PROFILE = 3
    const val MAX_DIAGNOSTIC_MODELS = 4
    const val TOTAL_REQUEST_BUDGET_MS = 62_000L
    const val DIAGNOSTIC_BUDGET_MS = 35_000L
    const val MIN_GROQ_ORG_COOLDOWN_MS = 60_000L

    const val GEMINI_SAFETY_MODE = "PROVIDER_DEFAULTS"

    fun temperatureFor(task: CortexTask): Double = when (task) {
        CortexTask.FAST -> FAST_TEMPERATURE
        CortexTask.GENERAL -> CONVERSATION_TEMPERATURE
        CortexTask.REASONING -> REASONING_TEMPERATURE
        CortexTask.CODING -> CODING_TEMPERATURE
    }

    fun tokenBudgetFor(task: CortexTask): Int = when (task) {
        CortexTask.FAST -> 500
        CortexTask.GENERAL -> 1_100
        CortexTask.REASONING,
        CortexTask.CODING -> 1_600
    }
}
