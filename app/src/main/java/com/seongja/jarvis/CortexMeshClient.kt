package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CortexMeshException(message: String) : Exception(message)

private class CortexTransportException(
    val statusCode: Int,
    val retryAfterMs: Long,
    message: String
) : Exception(message)

private data class TransportResult(
    val reply: String,
    val statusCode: Int,
    val elapsedMs: Long
)

class CortexMeshClient(private val store: SecureCortexRegistry) {

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val TOTAL_REQUEST_BUDGET_MS = 60_000L
        private const val DIAGNOSTIC_BUDGET_MS = 25_000L
    }

    fun ask(userInput: String, memoryContext: String): CortexMeshResult {
        val registry = store.load()
        val task = CortexTaskClassifier.classify(userInput)
        val now = System.currentTimeMillis()
        val configured = registry.profiles.filter { it.isConfigured() }

        if (configured.isEmpty()) {
            throw CortexMeshException("No Gemini or Groq cortex node is configured.")
        }

        val eligible = configured
            .filterNot { it.isCoolingDown(now) }
            .sortedByDescending { CortexMath.score(it, task, now) }
            .take(MAX_ATTEMPTS)
            .ifEmpty {
                val soonest = configured.minByOrNull { it.cooldownUntilMs }
                val seconds = soonest?.let {
                    ((it.cooldownUntilMs - now) / 1_000L).coerceAtLeast(1L)
                } ?: 1L
                throw CortexMeshException(
                    "Every configured cortex node is cooling down. Retry in about $seconds seconds."
                )
            }

        val attempts = mutableListOf<String>()
        val deadlineMs = System.currentTimeMillis() + TOTAL_REQUEST_BUDGET_MS
        for (profile in eligible) {
            if (System.currentTimeMillis() >= deadlineMs) break
            attempts += profile.label
            try {
                val result = request(
                    profile = profile,
                    systemPrompt = registry.systemPrompt,
                    userInput = userInput,
                    memoryContext = memoryContext,
                    task = task,
                    diagnostic = false,
                    deadlineMs = deadlineMs
                )
                markSuccess(profile, result)
                return CortexMeshResult(
                    reply = result.reply,
                    profileId = profile.id,
                    profileLabel = profile.label,
                    provider = profile.provider,
                    model = profile.model,
                    statusCode = result.statusCode,
                    elapsedMs = result.elapsedMs,
                    attempts = attempts.toList(),
                    task = task
                )
            } catch (error: CortexTransportException) {
                markFailure(profile, error)
            } catch (error: Exception) {
                markFailure(
                    profile,
                    CortexTransportException(
                        statusCode = 0,
                        retryAfterMs = 20_000L,
                        message = error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }

        val latest = store.load().profiles.associateBy { it.id }
        val failureSummary = attempts.joinToString("; ") { idOrLabel ->
            val profile = latest.values.firstOrNull { it.label == idOrLabel }
            if (profile == null) idOrLabel else "${profile.label}: ${profile.lastError.take(80)}"
        }
        throw CortexMeshException("All eligible cortex nodes failed. $failureSummary")
    }

    fun testProfile(profileId: String): CortexMeshResult {
        val registry = store.load()
        val profile = registry.profiles.firstOrNull { it.id == profileId }
            ?: throw CortexMeshException("Unknown cortex profile: $profileId")
        if (!profile.isConfigured()) {
            throw CortexMeshException("${profile.label} is not fully configured.")
        }

        return try {
            val result = request(
                profile = profile,
                systemPrompt = registry.systemPrompt,
                userInput = "Reply with exactly: CORTEX NODE ONLINE",
                memoryContext = "Connection diagnostic only.",
                task = CortexTask.FAST,
                diagnostic = true,
                deadlineMs = System.currentTimeMillis() + DIAGNOSTIC_BUDGET_MS
            )
            markSuccess(profile, result)
            CortexMeshResult(
                reply = result.reply,
                profileId = profile.id,
                profileLabel = profile.label,
                provider = profile.provider,
                model = profile.model,
                statusCode = result.statusCode,
                elapsedMs = result.elapsedMs,
                attempts = listOf(profile.label),
                task = CortexTask.FAST
            )
        } catch (error: CortexTransportException) {
            markFailure(profile, error)
            throw CortexMeshException("${profile.label}: ${error.message}")
        }
    }

    private fun request(
        profile: CortexProfile,
        systemPrompt: String,
        userInput: String,
        memoryContext: String,
        task: CortexTask,
        diagnostic: Boolean,
        deadlineMs: Long
    ): TransportResult {
        val temperature = when {
            diagnostic -> 0.0
            task == CortexTask.CODING -> 0.18
            task == CortexTask.REASONING -> 0.22
            else -> 0.30
        }
        val tokenBudget = when {
            diagnostic -> 32
            task == CortexTask.FAST -> 500
            task == CortexTask.GENERAL -> 1_100
            else -> 1_600
        }
        val operatingDirective = if (diagnostic) {
            "Connection diagnostic. Follow the requested exact output."
        } else {
            JarvisDirective.instructionFor(userInput, task)
        }
        val systemEnvelope = if (diagnostic) {
            "Connection diagnostic. Follow the requested exact output."
        } else {
            OwnerIdentityCore.systemEnvelope(
                editablePrompt = systemPrompt,
                taskDirective = operatingDirective,
                memoryContext = memoryContext
            )
        }

        val requestBody = JSONObject().apply {
            put("model", profile.model)
            put("temperature", temperature)
            put("max_tokens", tokenBudget)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemEnvelope)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userInput.take(12_000))
                    })
                }
            )
        }

        val started = System.currentTimeMillis()
        val remainingMs = (deadlineMs - started).coerceAtLeast(3_000L)
        val connection = (URL(profile.provider.endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000L.coerceAtMost(remainingMs).toInt()
            readTimeout = (if (diagnostic) 20_000L else 45_000L)
                .coerceAtMost(remainingMs)
                .toInt()
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${profile.apiKey.trim()}")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.6")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            val status = connection.responseCode
            val raw = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val apiMessage = extractApiError(raw).ifBlank { "HTTP $status" }
                val retryAfterMs = retryAfterMs(connection, status)
                throw CortexTransportException(status, retryAfterMs, apiMessage)
            }

            val reply = parseReply(raw)
            if (reply.isBlank()) {
                throw CortexTransportException(status, 10_000L, "Empty model response")
            }
            return TransportResult(reply, status, elapsed)
        } catch (error: CortexTransportException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw CortexTransportException(0, 25_000L, "Network timeout")
        } catch (error: Exception) {
            throw CortexTransportException(
                0,
                20_000L,
                error.message ?: error.javaClass.simpleName
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun markSuccess(profile: CortexProfile, result: TransportResult) {
        store.updateProfile(
            profile.copy(
                successes = profile.successes + 1,
                failureStreak = 0,
                requestCount = profile.requestCount + 1,
                lastLatencyMs = result.elapsedMs,
                lastUsedAtMs = System.currentTimeMillis(),
                cooldownUntilMs = 0L,
                lastStatusCode = result.statusCode,
                lastError = ""
            )
        )
    }

    private fun markFailure(profile: CortexProfile, error: CortexTransportException) {
        val now = System.currentTimeMillis()
        val authFailure = error.statusCode == 401 || error.statusCode == 403
        val cooldown = when {
            authFailure -> 0L
            error.statusCode == 429 -> error.retryAfterMs.coerceAtLeast(60_000L)
            error.statusCode in 500..599 -> error.retryAfterMs.coerceAtLeast(30_000L)
            else -> error.retryAfterMs.coerceAtLeast(15_000L)
        }
        store.updateProfile(
            profile.copy(
                enabled = if (authFailure) false else profile.enabled,
                failures = profile.failures + 1,
                failureStreak = profile.failureStreak + 1,
                requestCount = profile.requestCount + 1,
                lastUsedAtMs = now,
                cooldownUntilMs = if (cooldown > 0L) now + cooldown else 0L,
                lastStatusCode = error.statusCode,
                lastError = error.message.orEmpty().take(240)
            )
        )
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractApiError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?: root.optString("message")
    }.getOrNull().orEmpty()

    private fun parseReply(raw: String): String {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            val content = first?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (content.isNotBlank()) return content
            val text = first?.optString("text").orEmpty().trim()
            if (text.isNotBlank()) return text
        }
        val direct = root.optString("output_text").trim()
        if (direct.isNotBlank()) return direct
        throw CortexTransportException(200, 10_000L, "Unsupported response format")
    }

    private fun retryAfterMs(connection: HttpURLConnection, status: Int): Long {
        val seconds = connection.getHeaderField("Retry-After")
            ?.trim()
            ?.toLongOrNull()
        if (seconds != null) return seconds.coerceIn(1L, 3_600L) * 1_000L
        return when (status) {
            429 -> 90_000L
            in 500..599 -> 30_000L
            else -> 20_000L
        }
    }
}
