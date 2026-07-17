package com.seongja.jarvis

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiVisionException(message: String) : Exception(message)

data class VisionAnalysis(
    val description: String,
    val model: String,
    val profileLabel: String,
    val elapsedMs: Long
)

/**
 * Multimodal eye for FRIDAY. Camera frames remain temporary: the caller owns
 * the byte array and is responsible for deleting any capture file immediately.
 */
class GeminiVisionClient(context: Context) {
    private val registry = SecureCortexRegistry(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = profiles().isNotEmpty()

    fun analyze(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        question: String = DEFAULT_QUESTION
    ): VisionAnalysis {
        if (imageBytes.isEmpty()) throw GeminiVisionException("The camera frame was empty.")
        val candidates = profiles()
        if (candidates.isEmpty()) {
            throw GeminiVisionException(
                "No encrypted Gemini vision route is configured. Say configure APIs and add a Gemini key."
            )
        }

        val failures = mutableListOf<String>()
        candidates.forEach { profile ->
            runCatching {
                requestAnalysis(
                    profile = profile,
                    imageBytes = imageBytes,
                    mimeType = mimeType,
                    question = question
                )
            }.onSuccess { return it }
                .onFailure { error ->
                    failures += "${profile.label}/${profile.model}: ${safeError(error)}"
                }
        }

        throw GeminiVisionException(
            "Every Gemini vision route failed. ${failures.joinToString(" | ").take(760)}"
        )
    }

    private fun requestAnalysis(
        profile: CortexProfile,
        imageBytes: ByteArray,
        mimeType: String,
        question: String
    ): VisionAnalysis {
        val started = System.currentTimeMillis()
        val imagePart = JSONObject().put(
            "inline_data",
            JSONObject()
                .put("mime_type", mimeType)
                .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
        )
        val instruction = buildString {
            append("You are FRIDAY's visual perception module. Describe only what is actually visible. ")
            append("Do not identify a person by name, guess private traits, or invent obscured details. ")
            append("Be concise but useful. The owner asks: ")
            append(question.trim().ifBlank { DEFAULT_QUESTION }.take(1_200))
        }
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", instruction))
                            .put(imagePart)
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 700)
            )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${profile.model}:generateContent")
            .header("x-goog-api-key", profile.apiKey.trim())
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("").ifBlank { response.message }
                throw GeminiVisionException("HTTP ${response.code}: ${message.take(320)}")
            }

            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates") ?: JSONArray()
            val text = StringBuilder()
            for (candidateIndex in 0 until candidates.length()) {
                val parts = candidates.optJSONObject(candidateIndex)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: continue
                for (partIndex in 0 until parts.length()) {
                    val value = parts.optJSONObject(partIndex)?.optString("text").orEmpty().trim()
                    if (value.isNotBlank()) {
                        if (text.isNotEmpty()) text.append(' ')
                        text.append(value)
                    }
                }
            }
            val description = text.toString().replace(Regex("\\s+"), " ").trim()
            if (description.isBlank()) {
                val reason = root.optJSONObject("promptFeedback")
                    ?.optString("blockReason")
                    .orEmpty()
                throw GeminiVisionException(
                    if (reason.isBlank()) "The vision model returned no description."
                    else "Vision analysis was blocked: $reason"
                )
            }

            VisionAnalysis(
                description = description.take(2_400),
                model = profile.model,
                profileLabel = profile.label,
                elapsedMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun profiles(): List<CortexProfile> = registry.load().profiles
        .asSequence()
        .filter {
            it.provider == CortexProvider.GEMINI &&
                it.enabled &&
                it.apiKey.isNotBlank() &&
                it.model.isNotBlank() &&
                !it.isCoolingDown()
        }
        .sortedWith(compareByDescending<CortexProfile> { it.priority }.thenBy { it.failureStreak })
        .toList()

    private fun safeError(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(320)

    companion object {
        private const val DEFAULT_QUESTION =
            "Tell me what you can see, the important objects, visible text, and anything that needs my attention."
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
