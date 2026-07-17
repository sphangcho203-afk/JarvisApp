package com.seongja.jarvis

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiImageException(message: String) : Exception(message)

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val model: String,
    val prompt: String,
    val text: String,
    val elapsedMs: Long
)

class GeminiImageClient(context: Context) {
    private val registry = SecureCortexRegistry(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = geminiKeys().isNotEmpty()

    fun generate(
        prompt: String,
        aspectRatio: String = inferAspectRatio(prompt)
    ): GeneratedImage {
        val cleanPrompt = prompt.trim().take(4_000)
        if (cleanPrompt.isBlank()) throw GeminiImageException("An image description is required.")
        val keys = geminiKeys()
        if (keys.isEmpty()) {
            throw GeminiImageException("No encrypted Gemini API key is configured. Say configure APIs and add a Gemini key.")
        }

        val failures = mutableListOf<String>()
        keys.forEachIndexed { keyIndex, apiKey ->
            IMAGE_MODELS.forEach { model ->
                runCatching { requestImage(apiKey, model, cleanPrompt, aspectRatio) }
                    .onSuccess { return it }
                    .onFailure { error ->
                        failures += "K${keyIndex + 1}/$model: ${safeError(error)}"
                    }
            }
        }
        throw GeminiImageException(
            "Every Gemini image route failed. ${failures.joinToString(" | ").take(720)}"
        )
    }

    private fun requestImage(
        apiKey: String,
        model: String,
        prompt: String,
        aspectRatio: String
    ): GeneratedImage {
        val started = System.currentTimeMillis()
        val imageSpec = JSONObject().apply {
            put("aspectRatio", aspectRatio)
            if (!model.startsWith("gemini-2.5")) put("imageSize", "1K")
        }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("IMAGE"))
                    .put("responseFormat", JSONObject().put("image", imageSpec))
            )
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1/models/$model:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("").ifBlank { response.message }
                throw GeminiImageException("HTTP ${response.code}: ${message.take(280)}")
            }
            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates") ?: JSONArray()
            var imageData = ""
            var mimeType = "image/png"
            val text = StringBuilder()
            for (candidateIndex in 0 until candidates.length()) {
                val parts = candidates.optJSONObject(candidateIndex)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: continue
                for (partIndex in 0 until parts.length()) {
                    val part = parts.optJSONObject(partIndex) ?: continue
                    part.optString("text").trim().takeIf(String::isNotBlank)?.let {
                        if (text.isNotEmpty()) text.append(' ')
                        text.append(it)
                    }
                    val inline = part.optJSONObject("inlineData")
                        ?: part.optJSONObject("inline_data")
                    if (inline != null && imageData.isBlank()) {
                        imageData = inline.optString("data").trim()
                        mimeType = inline.optString("mimeType")
                            .ifBlank { inline.optString("mime_type") }
                            .ifBlank { "image/png" }
                    }
                }
            }
            if (imageData.isBlank()) {
                val blockReason = root.optJSONObject("promptFeedback")
                    ?.optString("blockReason")
                    .orEmpty()
                throw GeminiImageException(
                    if (blockReason.isBlank()) "The model returned no image data." else "Image generation blocked: $blockReason"
                )
            }
            val bytes = runCatching { Base64.decode(imageData, Base64.DEFAULT) }
                .getOrElse { throw GeminiImageException("The generated image payload could not be decoded.") }
            if (bytes.isEmpty()) throw GeminiImageException("The generated image payload was empty.")
            GeneratedImage(
                bytes = bytes,
                mimeType = mimeType,
                model = model,
                prompt = prompt,
                text = text.toString().replace(Regex("\\s+"), " ").trim().take(600),
                elapsedMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun geminiKeys(): List<String> = registry.load().profiles
        .asSequence()
        .filter { it.provider == CortexProvider.GEMINI && it.enabled && it.apiKey.isNotBlank() }
        .sortedByDescending { it.priority }
        .map { it.apiKey.trim() }
        .distinct()
        .toList()

    private fun safeError(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(300)

    companion object {
        val IMAGE_MODELS: List<String> = listOf(
            "gemini-3.1-flash-lite-image",
            "gemini-3.1-flash-image",
            "gemini-2.5-flash-image"
        )
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun inferAspectRatio(prompt: String): String {
            val lower = prompt.lowercase(Locale.US)
            return when {
                listOf("phone wallpaper", "mobile wallpaper", "portrait", "vertical", "story poster", "9:16").any(lower::contains) -> "9:16"
                listOf("desktop wallpaper", "landscape", "wide", "cinematic", "banner", "16:9").any(lower::contains) -> "16:9"
                listOf("poster", "cover", "3:4").any(lower::contains) -> "3:4"
                else -> "1:1"
            }
        }
    }
}
