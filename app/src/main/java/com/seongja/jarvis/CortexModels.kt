package com.seongja.jarvis

import java.util.Locale
import kotlin.math.exp

/** Fixed providers supported by the Phase 9 mesh. Endpoints are not user-editable. */
enum class CortexProvider(
    val displayName: String,
    val endpoint: String
) {
    GEMINI(
        displayName = "Gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    ),
    GROQ(
        displayName = "Groq",
        endpoint = "https://api.groq.com/openai/v1/chat/completions"
    )
}

enum class CortexTask {
    FAST,
    GENERAL,
    REASONING,
    CODING
}

data class CortexProfile(
    val id: String,
    val label: String,
    val provider: CortexProvider,
    val model: String = "",
    val apiKey: String = "",
    val enabled: Boolean = true,
    val priority: Int = 5,
    val successes: Int = 0,
    val failures: Int = 0,
    val failureStreak: Int = 0,
    val requestCount: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val lastUsedAtMs: Long = 0L,
    val cooldownUntilMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastError: String = ""
) {
    fun isConfigured(): Boolean = enabled && model.isNotBlank() && apiKey.isNotBlank()

    fun isCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean = cooldownUntilMs > nowMs

    fun healthLabel(nowMs: Long = System.currentTimeMillis()): String = when {
        !enabled -> "DISABLED"
        model.isBlank() || apiKey.isBlank() -> "NOT CONFIGURED"
        isCoolingDown(nowMs) -> "COOLDOWN ${((cooldownUntilMs - nowMs) / 1_000L).coerceAtLeast(1L)}s"
        lastStatusCode in 200..299 -> "ONLINE ${lastLatencyMs}ms"
        lastError.isNotBlank() -> "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"}"
        else -> "READY"
    }
}

data class CortexRegistry(
    val profiles: List<CortexProfile> = CortexDefaults.profiles(),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    fun configuredProfiles(): List<CortexProfile> = profiles.filter { it.isConfigured() }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Jarvis, Seongja's advanced phone assistant. Be precise, capable, calm, and concise. " +
                "Use supplied memory only when relevant. Android executes supported phone actions before requests reach you: " +
                "apps and web, flashlight, media controls, volume, brightness, rotation, battery and network telemetry, timers, " +
                "Spotify and YouTube search, and official connectivity/settings panels. Never claim an action succeeded unless " +
                "Android confirms it. Never invent device state. When uncertain, state the uncertainty instead of inventing facts."
    }
}

data class CortexMeshResult(
    val reply: String,
    val profileId: String,
    val profileLabel: String,
    val provider: CortexProvider,
    val model: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val attempts: List<String>,
    val task: CortexTask
)

object CortexDefaults {
    fun profiles(): List<CortexProfile> = buildList {
        repeat(6) { index ->
            add(
                CortexProfile(
                    id = "gemini_${index + 1}",
                    label = "GEMINI %02d".format(Locale.US, index + 1),
                    provider = CortexProvider.GEMINI,
                    priority = 7
                )
            )
        }
        repeat(4) { index ->
            add(
                CortexProfile(
                    id = "groq_${index + 1}",
                    label = "GROQ %02d".format(Locale.US, index + 1),
                    provider = CortexProvider.GROQ,
                    priority = 6
                )
            )
        }
    }
}

object CortexTaskClassifier {
    private val codingTerms = Regex(
        "\\b(code|coding|kotlin|java|python|javascript|typescript|gradle|github|compile|compiler|debug|bug|function|class|api|json|sql|html|css)\\b",
        RegexOption.IGNORE_CASE
    )
    private val reasoningTerms = Regex(
        "\\b(analy[sz]e|reason|strategy|plan|compare|evaluate|derive|prove|calculate|mathematics|physics|architecture|optimi[sz]e|why|deep)\\b",
        RegexOption.IGNORE_CASE
    )

    fun classify(input: String): CortexTask {
        val clean = input.trim()
        if (codingTerms.containsMatchIn(clean)) return CortexTask.CODING
        if (reasoningTerms.containsMatchIn(clean) || clean.length > 260) return CortexTask.REASONING
        if (clean.length <= 48 && clean.split(Regex("\\s+")).size <= 8) return CortexTask.FAST
        return CortexTask.GENERAL
    }
}

/**
 * Provider selection uses a bounded utility score rather than blind key rotation.
 * Reliability is the posterior mean of a Beta(1,1) model.
 * Latency and freshness use exponential response curves so one bad sample cannot dominate forever.
 */
object CortexMath {
    fun score(profile: CortexProfile, task: CortexTask, nowMs: Long): Double {
        if (!profile.isConfigured() || profile.isCoolingDown(nowMs)) return Double.NEGATIVE_INFINITY

        val taskFit = taskFit(profile, task)
        val reliability = (profile.successes + 1.0) / (profile.successes + profile.failures + 2.0)
        val latencyUtility = if (profile.lastLatencyMs <= 0L) {
            0.72
        } else {
            exp(-profile.lastLatencyMs.coerceAtMost(30_000L) / 3_500.0)
        }
        val idleMs = if (profile.lastUsedAtMs <= 0L) 180_000L else (nowMs - profile.lastUsedAtMs).coerceAtLeast(0L)
        val freshness = 1.0 - exp(-idleMs / 45_000.0)
        val priorityUtility = profile.priority.coerceIn(1, 10) / 10.0
        val stability = exp(-profile.failureStreak.coerceAtMost(8) / 2.5)

        val weighted =
            0.38 * taskFit +
                0.22 * reliability +
                0.16 * latencyUtility +
                0.14 * freshness +
                0.10 * priorityUtility

        return weighted * stability
    }

    private fun taskFit(profile: CortexProfile, task: CortexTask): Double {
        val model = profile.model.lowercase(Locale.US)
        var fit = when (task) {
            CortexTask.FAST -> if (profile.provider == CortexProvider.GROQ) 1.0 else 0.86
            CortexTask.GENERAL -> if (profile.provider == CortexProvider.GEMINI) 0.98 else 0.90
            CortexTask.REASONING -> if (profile.provider == CortexProvider.GEMINI) 1.0 else 0.92
            CortexTask.CODING -> if (profile.provider == CortexProvider.GROQ) 0.98 else 0.94
        }

        if (task == CortexTask.FAST && ("flash" in model || "instant" in model || "8b" in model)) fit += 0.05
        if (task == CortexTask.REASONING && ("pro" in model || "70b" in model || "32b" in model)) fit += 0.05
        if (task == CortexTask.CODING && ("qwen" in model || "coder" in model || "code" in model)) fit += 0.06
        return fit.coerceIn(0.0, 1.0)
    }
}
