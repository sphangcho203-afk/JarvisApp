package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ProviderMeshCategory(val label: String) {
    AI("AI + REASONING"),
    RESEARCH("RESEARCH + SEARCH"),
    WEATHER("WEATHER + ENVIRONMENT"),
    VOICE("VOICE + AUDIO"),
    DATA("DATA + SERVICES"),
    CUSTOM("CUSTOM ENDPOINT")
}

enum class ProviderMeshProtocol {
    OPENAI_CHAT,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE,
    COHERE_CHAT,
    TAVILY_SEARCH,
    EXA_SEARCH,
    OPENWEATHER,
    WEATHER_API,
    TOMORROW_WEATHER,
    VISUAL_CROSSING,
    CARTESIA,
    ELEVENLABS,
    YOUTUBE_DATA,
    API_KEY_BEARER,
    API_KEY_QUERY
}

data class ProviderPreset(
    val id: String,
    val label: String,
    val company: String,
    val category: ProviderMeshCategory,
    val protocol: ProviderMeshProtocol,
    val baseUrl: String,
    val invokePath: String,
    val modelsPath: String = "",
    val defaultModel: String = "",
    val aliases: List<String> = emptyList(),
    val note: String = ""
)

object ProviderMeshCatalog {
    val presets: List<ProviderPreset> = listOf(
        ProviderPreset("openai", "OpenAI", "OpenAI", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_RESPONSES, "https://api.openai.com/v1", "/responses", "/models", aliases = listOf("open ai", "chat gpt", "gpt"), note = "Responses API with live model discovery"),
        ProviderPreset("anthropic", "Anthropic Claude", "Anthropic", ProviderMeshCategory.AI, ProviderMeshProtocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1", "/messages", "/models", aliases = listOf("claude", "anthropic")),
        ProviderPreset("gemini", "Google Gemini", "Google", ProviderMeshCategory.AI, ProviderMeshProtocol.GEMINI_GENERATE, "https://generativelanguage.googleapis.com/v1beta", "/models/{model}:generateContent", "/models", aliases = listOf("gemini", "google ai")),
        ProviderPreset("deepseek", "DeepSeek", "DeepSeek", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://api.deepseek.com/v1", "/chat/completions", "/models", aliases = listOf("deep seek", "deepseek")),
        ProviderPreset("groq", "Groq", "Groq", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://api.groq.com/openai/v1", "/chat/completions", "/models", aliases = listOf("groq")),
        ProviderPreset("mistral", "Mistral AI", "Mistral", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://api.mistral.ai/v1", "/chat/completions", "/models", aliases = listOf("mistral")),
        ProviderPreset("xai", "xAI Grok", "xAI", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://api.x.ai/v1", "/chat/completions", "/models", aliases = listOf("x ai", "grok", "xai")),
        ProviderPreset("cohere", "Cohere", "Cohere", ProviderMeshCategory.AI, ProviderMeshProtocol.COHERE_CHAT, "https://api.cohere.com", "/v2/chat", "/v1/models", aliases = listOf("cohere")),
        ProviderPreset("perplexity", "Perplexity Sonar", "Perplexity", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://api.perplexity.ai", "/v1/sonar", "/v1/models", aliases = listOf("perplexity", "sonar"), note = "Grounded answer and agent models"),
        ProviderPreset("openrouter", "OpenRouter", "OpenRouter", ProviderMeshCategory.AI, ProviderMeshProtocol.OPENAI_CHAT, "https://openrouter.ai/api/v1", "/chat/completions", "/models", aliases = listOf("open router", "openrouter"), note = "Multi-company model catalog"),
        ProviderPreset("custom_openai", "Custom OpenAI-Compatible", "Custom", ProviderMeshCategory.CUSTOM, ProviderMeshProtocol.OPENAI_CHAT, "", "/chat/completions", "/models", aliases = listOf("custom api", "custom endpoint", "local ai")),
        ProviderPreset("tavily", "Tavily", "Tavily", ProviderMeshCategory.RESEARCH, ProviderMeshProtocol.TAVILY_SEARCH, "https://api.tavily.com", "/search", aliases = listOf("tavily")),
        ProviderPreset("exa", "Exa", "Exa", ProviderMeshCategory.RESEARCH, ProviderMeshProtocol.EXA_SEARCH, "https://api.exa.ai", "/search", aliases = listOf("exa")),
        ProviderPreset("openweather", "OpenWeather", "OpenWeather", ProviderMeshCategory.WEATHER, ProviderMeshProtocol.OPENWEATHER, "https://api.openweathermap.org", "/data/2.5/weather", aliases = listOf("open weather", "openweather")),
        ProviderPreset("weatherapi", "WeatherAPI", "WeatherAPI", ProviderMeshCategory.WEATHER, ProviderMeshProtocol.WEATHER_API, "https://api.weatherapi.com", "/v1/current.json", aliases = listOf("weather api", "weatherapi")),
        ProviderPreset("tomorrow", "Tomorrow.io", "Tomorrow.io", ProviderMeshCategory.WEATHER, ProviderMeshProtocol.TOMORROW_WEATHER, "https://api.tomorrow.io", "/v4/weather/realtime", aliases = listOf("tomorrow io", "tomorrow weather")),
        ProviderPreset("visualcrossing", "Visual Crossing", "Visual Crossing", ProviderMeshCategory.WEATHER, ProviderMeshProtocol.VISUAL_CROSSING, "https://weather.visualcrossing.com", "/VisualCrossingWebServices/rest/services/timeline/London/today", aliases = listOf("visual crossing")),
        ProviderPreset("cartesia", "Cartesia", "Cartesia", ProviderMeshCategory.VOICE, ProviderMeshProtocol.CARTESIA, "https://api.cartesia.ai", "/models", aliases = listOf("cartesia")),
        ProviderPreset("elevenlabs", "ElevenLabs", "ElevenLabs", ProviderMeshCategory.VOICE, ProviderMeshProtocol.ELEVENLABS, "https://api.elevenlabs.io", "/v1/models", aliases = listOf("eleven labs", "elevenlabs")),
        ProviderPreset("youtube", "YouTube Data API", "Google", ProviderMeshCategory.DATA, ProviderMeshProtocol.YOUTUBE_DATA, "https://www.googleapis.com", "/youtube/v3/search", aliases = listOf("youtube api", "youtube data")),
        ProviderPreset("googlemaps", "Google Maps Platform", "Google", ProviderMeshCategory.DATA, ProviderMeshProtocol.API_KEY_QUERY, "https://maps.googleapis.com", "/maps/api/geocode/json", aliases = listOf("google maps", "maps api")),
        ProviderPreset("spotify", "Spotify Web API", "Spotify", ProviderMeshCategory.DATA, ProviderMeshProtocol.API_KEY_BEARER, "https://api.spotify.com", "/v1/me", aliases = listOf("spotify api", "spotify"), note = "OAuth access token required; static project keys are not sufficient")
    )

    fun byId(id: String): ProviderPreset? = presets.firstOrNull { it.id == id }

    fun match(text: String): ProviderPreset? {
        val lower = text.lowercase(Locale.US)
        return presets
            .sortedByDescending { preset -> (preset.aliases + preset.label.lowercase(Locale.US)).maxOfOrNull(String::length) ?: 0 }
            .firstOrNull { preset ->
                (preset.aliases + preset.id + preset.label.lowercase(Locale.US)).any { alias -> lower.contains(alias.lowercase(Locale.US)) }
            }
    }
}

data class ProviderMeshProfile(
    val id: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val priority: Int = 5,
    val lastStatusCode: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastError: String = "",
    val lastVerifiedAtMs: Long = 0L,
    val discoveredModels: List<String> = emptyList()
) {
    fun isConfigured(): Boolean = enabled && apiKey.isNotBlank() && resolvedBaseUrl().isNotBlank()
    fun resolvedBaseUrl(): String = baseUrl.trim().trimEnd('/')
    fun healthLabel(): String = when {
        !enabled -> "DISABLED"
        apiKey.isBlank() -> "KEY REQUIRED"
        lastStatusCode in 200..299 -> "ONLINE"
        lastStatusCode > 0 -> "HTTP $lastStatusCode"
        else -> "CONFIGURED"
    }
}

data class ProviderMeshRegistry(
    val profiles: List<ProviderMeshProfile> = ProviderMeshCatalog.presets.map { preset ->
        ProviderMeshProfile(id = preset.id, baseUrl = preset.baseUrl, model = preset.defaultModel)
    },
    val defaultAiProviderId: String = ""
) {
    fun profile(id: String): ProviderMeshProfile = profiles.firstOrNull { it.id == id }
        ?: ProviderMeshCatalog.byId(id)?.let { ProviderMeshProfile(it.id, it.baseUrl, model = it.defaultModel) }
        ?: ProviderMeshProfile(id)

    fun configuredAiProfiles(): List<ProviderMeshProfile> = profiles
        .filter { profile ->
            val preset = ProviderMeshCatalog.byId(profile.id)
            preset?.category in setOf(ProviderMeshCategory.AI, ProviderMeshCategory.CUSTOM) && profile.isConfigured() && profile.model.isNotBlank()
        }
        .sortedWith(compareBy<ProviderMeshProfile> { if (it.id == defaultAiProviderId) 0 else 1 }.thenBy { it.priority })
}

class SecureProviderMeshRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): ProviderMeshRegistry {
        val raw = decrypt(prefs.getString(KEY_REGISTRY, "").orEmpty())
        if (raw.isBlank()) return ProviderMeshRegistry()
        return runCatching { normalize(decode(raw)) }.getOrElse { ProviderMeshRegistry() }
    }

    @Synchronized
    fun save(registry: ProviderMeshRegistry) {
        prefs.edit().putString(KEY_REGISTRY, encrypt(encode(normalize(registry)))).apply()
    }

    @Synchronized
    fun updateProfile(profile: ProviderMeshProfile, makeDefaultAi: Boolean = false) {
        val current = load()
        val next = current.profiles.filterNot { it.id == profile.id } + profile
        val preset = ProviderMeshCatalog.byId(profile.id)
        save(current.copy(
            profiles = next,
            defaultAiProviderId = if (makeDefaultAi && preset?.category in setOf(ProviderMeshCategory.AI, ProviderMeshCategory.CUSTOM)) profile.id else current.defaultAiProviderId
        ))
    }

    private fun normalize(registry: ProviderMeshRegistry): ProviderMeshRegistry {
        val existing = registry.profiles.associateBy { it.id }
        val normalized = ProviderMeshCatalog.presets.map { preset ->
            val profile = existing[preset.id] ?: ProviderMeshProfile(preset.id, preset.baseUrl, model = preset.defaultModel)
            profile.copy(
                id = preset.id,
                baseUrl = profile.baseUrl.ifBlank { preset.baseUrl },
                discoveredModels = profile.discoveredModels.distinct().take(250)
            )
        }
        return registry.copy(profiles = normalized)
    }

    private fun encode(registry: ProviderMeshRegistry): String = JSONObject().apply {
        put("defaultAiProviderId", registry.defaultAiProviderId)
        put("profiles", JSONArray().apply {
            registry.profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("id", profile.id)
                    put("baseUrl", profile.baseUrl)
                    put("apiKey", profile.apiKey)
                    put("model", profile.model)
                    put("enabled", profile.enabled)
                    put("priority", profile.priority)
                    put("lastStatusCode", profile.lastStatusCode)
                    put("lastLatencyMs", profile.lastLatencyMs)
                    put("lastError", profile.lastError)
                    put("lastVerifiedAtMs", profile.lastVerifiedAtMs)
                    put("discoveredModels", JSONArray(profile.discoveredModels))
                })
            }
        })
    }.toString()

    private fun decode(raw: String): ProviderMeshRegistry {
        val root = JSONObject(raw)
        val profiles = buildList {
            val array = root.optJSONArray("profiles") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val models = buildList {
                    val modelArray = item.optJSONArray("discoveredModels") ?: JSONArray()
                    for (modelIndex in 0 until modelArray.length()) add(modelArray.optString(modelIndex))
                }.filter(String::isNotBlank)
                add(ProviderMeshProfile(
                    id = item.optString("id"),
                    baseUrl = item.optString("baseUrl"),
                    apiKey = item.optString("apiKey"),
                    model = item.optString("model"),
                    enabled = item.optBoolean("enabled"),
                    priority = item.optInt("priority", 5),
                    lastStatusCode = item.optInt("lastStatusCode"),
                    lastLatencyMs = item.optLong("lastLatencyMs"),
                    lastError = item.optString("lastError"),
                    lastVerifiedAtMs = item.optLong("lastVerifiedAtMs"),
                    discoveredModels = models
                ))
            }
        }
        return ProviderMeshRegistry(profiles, root.optString("defaultAiProviderId"))
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            require(payload.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, payload.copyOfRange(0, IV_SIZE)))
            String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "friday_universal_provider_mesh"
        private const val KEY_REGISTRY = "registry"
        private const val KEY_ALIAS = "friday_universal_provider_mesh_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}

data class ProviderMeshTestResult(
    val providerId: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val models: List<String> = emptyList(),
    val detail: String = ""
)

data class UniversalProviderAnswer(
    val reply: String,
    val providerId: String,
    val providerLabel: String,
    val model: String,
    val statusCode: Int,
    val elapsedMs: Long
)

class UniversalProviderMeshClient(private val store: SecureProviderMeshRegistry) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = store.load().configuredAiProfiles().isNotEmpty()
    fun activeLabel(): String {
        val registry = store.load()
        val active = registry.configuredAiProfiles().firstOrNull() ?: return "OFFLINE"
        return "${ProviderMeshCatalog.byId(active.id)?.label ?: active.id} // ${active.model}"
    }

    fun ask(input: String, localContext: String): UniversalProviderAnswer {
        val profiles = store.load().configuredAiProfiles()
        require(profiles.isNotEmpty()) { "No universal AI provider is configured." }
        val failures = mutableListOf<String>()
        profiles.forEach { profile ->
            val preset = ProviderMeshCatalog.byId(profile.id) ?: return@forEach
            val result = runCatching { invoke(preset, profile, input, localContext) }
            result.onSuccess { return it }
            failures += "${preset.label}: ${result.exceptionOrNull()?.message ?: "failed"}"
        }
        throw IllegalStateException("Universal provider mesh failed: ${failures.joinToString(" | ").take(480)}")
    }

    fun listModels(providerId: String): List<String> {
        val preset = requireNotNull(ProviderMeshCatalog.byId(providerId)) { "Unknown provider." }
        val profile = store.load().profile(providerId)
        require(profile.apiKey.isNotBlank()) { "API key or access token required." }
        require(preset.modelsPath.isNotBlank()) { "${preset.label} does not expose a model catalog through this route." }
        val request = authenticatedRequest(preset, profile, modelListUrl(preset, profile)).get().build()
        val started = System.currentTimeMillis()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${errorText(body)}")
            val models = parseModels(preset, body)
            val updated = profile.copy(
                lastStatusCode = response.code,
                lastLatencyMs = System.currentTimeMillis() - started,
                lastError = "",
                lastVerifiedAtMs = System.currentTimeMillis(),
                discoveredModels = models
            )
            store.updateProfile(updated)
            return models
        }
    }

    fun test(providerId: String): ProviderMeshTestResult {
        val preset = requireNotNull(ProviderMeshCatalog.byId(providerId)) { "Unknown provider." }
        val profile = store.load().profile(providerId)
        require(profile.apiKey.isNotBlank()) { "API key or access token required." }
        val started = System.currentTimeMillis()
        return runCatching {
            val result = when (preset.protocol) {
                ProviderMeshProtocol.OPENAI_CHAT,
                ProviderMeshProtocol.OPENAI_RESPONSES,
                ProviderMeshProtocol.ANTHROPIC_MESSAGES,
                ProviderMeshProtocol.GEMINI_GENERATE,
                ProviderMeshProtocol.COHERE_CHAT -> {
                    val models = listModels(providerId)
                    ProviderMeshTestResult(providerId, 200, System.currentTimeMillis() - started, models, "MODEL CATALOG VERIFIED")
                }
                else -> testService(preset, profile, started)
            }
            result
        }.onFailure { error ->
            store.updateProfile(profile.copy(
                lastStatusCode = extractStatus(error.message),
                lastLatencyMs = System.currentTimeMillis() - started,
                lastError = (error.message ?: error.javaClass.simpleName).take(300),
                lastVerifiedAtMs = System.currentTimeMillis()
            ))
        }.getOrThrow()
    }

    private fun invoke(preset: ProviderPreset, profile: ProviderMeshProfile, input: String, localContext: String): UniversalProviderAnswer {
        require(profile.model.isNotBlank()) { "Choose a model for ${preset.label}." }
        val started = System.currentTimeMillis()
        val request = when (preset.protocol) {
            ProviderMeshProtocol.OPENAI_RESPONSES -> {
                val body = JSONObject().apply {
                    put("model", profile.model)
                    put("instructions", "You are F.R.I.D.A.Y., Seongja's private Android intelligence. Be truthful, concise, privacy-preserving, and never claim an action succeeded without device verification.")
                    put("input", buildPrompt(localContext, input))
                    put("max_output_tokens", 1200)
                    put("store", false)
                }
                authenticatedRequest(preset, profile, serviceUrl(preset, profile, preset.invokePath)).post(body.toString().toRequestBody(json)).build()
            }
            ProviderMeshProtocol.ANTHROPIC_MESSAGES -> {
                val body = JSONObject().apply {
                    put("model", profile.model)
                    put("max_tokens", 1200)
                    put("system", "You are F.R.I.D.A.Y., Seongja's private Android intelligence. Be truthful, concise, privacy-preserving, and never claim an action succeeded without device verification.")
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", buildPrompt(localContext, input))))
                }
                authenticatedRequest(preset, profile, serviceUrl(preset, profile, preset.invokePath)).post(body.toString().toRequestBody(json)).build()
            }
            ProviderMeshProtocol.GEMINI_GENERATE -> {
                val body = JSONObject().apply {
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are F.R.I.D.A.Y., Seongja's private Android intelligence. Be truthful and privacy-preserving."))))
                    put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", buildPrompt(localContext, input))))))
                    put("generationConfig", JSONObject().put("maxOutputTokens", 1200))
                }
                val path = preset.invokePath.replace("{model}", profile.model.removePrefix("models/"))
                authenticatedRequest(preset, profile, serviceUrl(preset, profile, path)).post(body.toString().toRequestBody(json)).build()
            }
            ProviderMeshProtocol.COHERE_CHAT -> {
                val body = JSONObject().apply {
                    put("model", profile.model)
                    put("stream", false)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "You are F.R.I.D.A.Y., Seongja's private Android intelligence. Be truthful and privacy-preserving."))
                        .put(JSONObject().put("role", "user").put("content", buildPrompt(localContext, input))))
                }
                authenticatedRequest(preset, profile, serviceUrl(preset, profile, preset.invokePath)).post(body.toString().toRequestBody(json)).build()
            }
            else -> {
                val body = JSONObject().apply {
                    put("model", profile.model)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "You are F.R.I.D.A.Y., Seongja's private Android intelligence. Be truthful, concise, and privacy-preserving."))
                        .put(JSONObject().put("role", "user").put("content", buildPrompt(localContext, input))))
                    put("max_tokens", 1200)
                    put("temperature", .35)
                }
                authenticatedRequest(preset, profile, serviceUrl(preset, profile, preset.invokePath)).post(body.toString().toRequestBody(json)).build()
            }
        }
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val elapsed = System.currentTimeMillis() - started
            if (!response.isSuccessful) {
                store.updateProfile(profile.copy(lastStatusCode = response.code, lastLatencyMs = elapsed, lastError = errorText(raw), lastVerifiedAtMs = System.currentTimeMillis()))
                throw IllegalStateException("HTTP ${response.code}: ${errorText(raw)}")
            }
            val reply = parseReply(preset.protocol, raw).trim()
            require(reply.isNotBlank()) { "${preset.label} returned an empty response." }
            store.updateProfile(profile.copy(lastStatusCode = response.code, lastLatencyMs = elapsed, lastError = "", lastVerifiedAtMs = System.currentTimeMillis()))
            return UniversalProviderAnswer(reply, profile.id, preset.label, profile.model, response.code, elapsed)
        }
    }

    private fun testService(preset: ProviderPreset, profile: ProviderMeshProfile, started: Long): ProviderMeshTestResult {
        val url = when (preset.protocol) {
            ProviderMeshProtocol.TAVILY_SEARCH -> serviceUrl(preset, profile, preset.invokePath)
            ProviderMeshProtocol.EXA_SEARCH -> serviceUrl(preset, profile, preset.invokePath)
            ProviderMeshProtocol.OPENWEATHER -> serviceUrl(preset, profile, preset.invokePath) + "?q=London&appid=${profile.apiKey}"
            ProviderMeshProtocol.WEATHER_API -> serviceUrl(preset, profile, preset.invokePath) + "?key=${profile.apiKey}&q=London"
            ProviderMeshProtocol.TOMORROW_WEATHER -> serviceUrl(preset, profile, preset.invokePath) + "?location=London&apikey=${profile.apiKey}"
            ProviderMeshProtocol.VISUAL_CROSSING -> serviceUrl(preset, profile, preset.invokePath) + "?unitGroup=metric&include=current&key=${profile.apiKey}"
            ProviderMeshProtocol.ELEVENLABS -> serviceUrl(preset, profile, preset.invokePath)
            ProviderMeshProtocol.CARTESIA -> serviceUrl(preset, profile, preset.invokePath)
            ProviderMeshProtocol.YOUTUBE_DATA -> serviceUrl(preset, profile, preset.invokePath) + "?part=snippet&q=FRIDAY&type=video&maxResults=1&key=${profile.apiKey}"
            ProviderMeshProtocol.API_KEY_QUERY -> serviceUrl(preset, profile, preset.invokePath) + "?address=London&key=${profile.apiKey}"
            else -> serviceUrl(preset, profile, preset.invokePath)
        }
        val builder = authenticatedRequest(preset, profile, url)
        val request = when (preset.protocol) {
            ProviderMeshProtocol.TAVILY_SEARCH -> builder.post(JSONObject().put("api_key", profile.apiKey).put("query", "OpenAI").put("max_results", 1).toString().toRequestBody(json)).build()
            ProviderMeshProtocol.EXA_SEARCH -> builder.post(JSONObject().put("query", "OpenAI").put("numResults", 1).toString().toRequestBody(json)).build()
            else -> builder.get().build()
        }
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val elapsed = System.currentTimeMillis() - started
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${errorText(raw)}")
            store.updateProfile(profile.copy(lastStatusCode = response.code, lastLatencyMs = elapsed, lastError = "", lastVerifiedAtMs = System.currentTimeMillis()))
            return ProviderMeshTestResult(profile.id, response.code, elapsed, detail = "SERVICE ROUTE VERIFIED")
        }
    }

    private fun authenticatedRequest(preset: ProviderPreset, profile: ProviderMeshProfile, url: String): Request.Builder {
        require(url.startsWith("https://")) { "Only HTTPS provider endpoints are accepted." }
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when (preset.protocol) {
            ProviderMeshProtocol.ANTHROPIC_MESSAGES -> builder.header("x-api-key", profile.apiKey).header("anthropic-version", "2023-06-01")
            ProviderMeshProtocol.GEMINI_GENERATE,
            ProviderMeshProtocol.OPENWEATHER,
            ProviderMeshProtocol.WEATHER_API,
            ProviderMeshProtocol.TOMORROW_WEATHER,
            ProviderMeshProtocol.VISUAL_CROSSING,
            ProviderMeshProtocol.YOUTUBE_DATA,
            ProviderMeshProtocol.API_KEY_QUERY,
            ProviderMeshProtocol.TAVILY_SEARCH -> Unit
            ProviderMeshProtocol.EXA_SEARCH -> builder.header("x-api-key", profile.apiKey)
            ProviderMeshProtocol.ELEVENLABS -> builder.header("xi-api-key", profile.apiKey)
            ProviderMeshProtocol.CARTESIA -> builder.header("X-API-Key", profile.apiKey).header("Cartesia-Version", "2025-04-16")
            else -> builder.header("Authorization", "Bearer ${profile.apiKey}")
        }
        if (preset.id == "openrouter") {
            builder.header("HTTP-Referer", "https://github.com/sphangcho203-afk/JarvisApp")
            builder.header("X-OpenRouter-Title", "F.R.I.D.A.Y.")
        }
        return builder
    }

    private fun modelListUrl(preset: ProviderPreset, profile: ProviderMeshProfile): String {
        val base = serviceUrl(preset, profile, preset.modelsPath)
        return if (preset.protocol == ProviderMeshProtocol.GEMINI_GENERATE) "$base?key=${profile.apiKey}" else base
    }

    private fun serviceUrl(preset: ProviderPreset, profile: ProviderMeshProfile, path: String): String {
        val base = profile.resolvedBaseUrl().ifBlank { preset.baseUrl.trimEnd('/') }
        require(base.startsWith("https://")) { "A secure HTTPS base URL is required." }
        return base + if (path.startsWith('/')) path else "/$path"
    }

    private fun parseModels(preset: ProviderPreset, raw: String): List<String> {
        val root = JSONObject(raw)
        val array = when (preset.protocol) {
            ProviderMeshProtocol.GEMINI_GENERATE -> root.optJSONArray("models")
            ProviderMeshProtocol.COHERE_CHAT -> root.optJSONArray("models")
            else -> root.optJSONArray("data")
        } ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = when (preset.protocol) {
                    ProviderMeshProtocol.GEMINI_GENERATE -> item.optString("name").removePrefix("models/")
                    ProviderMeshProtocol.COHERE_CHAT -> item.optString("name")
                    else -> item.optString("id")
                }
                if (id.isNotBlank()) add(id)
            }
        }.distinct().sorted().take(250)
    }

    private fun parseReply(protocol: ProviderMeshProtocol, raw: String): String {
        val root = JSONObject(raw)
        return when (protocol) {
            ProviderMeshProtocol.OPENAI_RESPONSES -> {
                root.optString("output_text").ifBlank {
                    val output = root.optJSONArray("output") ?: JSONArray()
                    buildString {
                        for (index in 0 until output.length()) {
                            val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
                            for (contentIndex in 0 until content.length()) {
                                val text = content.optJSONObject(contentIndex)?.optString("text").orEmpty()
                                if (text.isNotBlank()) append(text)
                            }
                        }
                    }
                }
            }
            ProviderMeshProtocol.ANTHROPIC_MESSAGES -> {
                val content = root.optJSONArray("content") ?: JSONArray()
                buildString {
                    for (index in 0 until content.length()) {
                        val text = content.optJSONObject(index)?.optString("text").orEmpty()
                        if (text.isNotBlank()) append(text)
                    }
                }
            }
            ProviderMeshProtocol.GEMINI_GENERATE -> root.optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text").orEmpty()
            ProviderMeshProtocol.COHERE_CHAT -> root.optJSONObject("message")
                ?.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            else -> root.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
        }
    }

    private fun buildPrompt(localContext: String, input: String): String = buildString {
        if (localContext.isNotBlank()) {
            appendLine("Relevant encrypted local context:")
            appendLine(localContext.take(5000))
            appendLine()
        }
        append("Owner request: ")
        append(input)
    }

    private fun errorText(raw: String): String = runCatching {
        val root = JSONObject(raw)
        when {
            root.optJSONObject("error") != null -> root.optJSONObject("error")?.optString("message")
            root.has("message") -> root.optString("message")
            else -> raw
        }.orEmpty().ifBlank { "Unknown provider error" }.take(360)
    }.getOrElse { raw.replace(Regex("\\s+"), " ").take(360) }

    private fun extractStatus(message: String?): Int = Regex("HTTP\\s+(\\d{3})", RegexOption.IGNORE_CASE)
        .find(message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

sealed class ProviderMeshCommand {
    data class Open(val providerId: String? = null, val category: ProviderMeshCategory? = null, val discoverModels: Boolean = false) : ProviderMeshCommand()
    data class Activate(val providerId: String, val model: String = "") : ProviderMeshCommand()
    data object Status : ProviderMeshCommand()
}

object ProviderMeshCommandParser {
    fun parse(input: String): ProviderMeshCommand? {
        val lower = input.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        val preset = ProviderMeshCatalog.match(lower)
        if (lower in setOf("provider mesh status", "universal api status", "all api status")) return ProviderMeshCommand.Status
        if (preset != null) {
            Regex("(?:use|select|activate|make)\\s+.+?\\s+(?:model\\s+)?(?:to\\s+)?([a-zA-Z0-9._:/~-]+)$", RegexOption.IGNORE_CASE)
                .find(input.trim())?.groupValues?.getOrNull(1)?.let { model ->
                    return ProviderMeshCommand.Activate(preset.id, model)
                }
            if (listOf("use ", "activate ", "make default ").any(lower::startsWith) && !lower.contains("setup")) {
                return ProviderMeshCommand.Activate(preset.id)
            }
            val setup = listOf("setup", "set up", "configure", "configuration", "api", "models").any(lower::contains)
            val open = listOf("open", "show", "launch", "manage", "configure", "setup", "set up").any(lower::contains)
            if (setup && open) return ProviderMeshCommand.Open(preset.id, discoverModels = lower.contains("model"))
        }
        if (lower.contains("weather") && listOf("setup", "set up", "configure", "api").any(lower::contains)) return ProviderMeshCommand.Open(category = ProviderMeshCategory.WEATHER)
        if ((lower.contains("research") || lower.contains("search api")) && listOf("setup", "set up", "configure", "api").any(lower::contains)) return ProviderMeshCommand.Open(category = ProviderMeshCategory.RESEARCH)
        if ((lower.contains("voice api") || lower.contains("speech api")) && listOf("setup", "set up", "configure").any(lower::contains)) return ProviderMeshCommand.Open(category = ProviderMeshCategory.VOICE)
        if (lower in setOf("configure apis", "open api setup", "open api settings", "open provider mesh", "manage apis", "api setup")) return ProviderMeshCommand.Open()
        return null
    }
}

class ProviderMeshCommandRouter(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureProviderMeshRegistry(appContext)

    fun intercept(input: String, memorySummary: String): BrainResponse? {
        return when (val command = ProviderMeshCommandParser.parse(input) ?: return null) {
            is ProviderMeshCommand.Open -> {
                ProviderMeshActivity.launch(appContext, command.providerId, command.category, command.discoverModels)
                val target = command.providerId?.let { ProviderMeshCatalog.byId(it)?.label }
                    ?: command.category?.label ?: "UNIVERSAL PROVIDER MESH"
                response(
                    spoken = "Opening $target setup, Sir.",
                    display = "PROVIDER MESH // $target\nCREDENTIAL STORAGE // ANDROID KEYSTORE AES-GCM\nMODEL DISCOVERY // LIVE WHEN SUPPORTED",
                    intent = "provider_mesh/open",
                    memorySummary = memorySummary,
                    decision = "launch_provider_mesh"
                )
            }
            is ProviderMeshCommand.Activate -> {
                val preset = requireNotNull(ProviderMeshCatalog.byId(command.providerId))
                val current = store.load().profile(command.providerId)
                val updated = current.copy(
                    enabled = true,
                    model = command.model.ifBlank { current.model },
                    lastError = ""
                )
                store.updateProfile(updated, makeDefaultAi = true)
                response(
                    spoken = if (updated.model.isBlank()) {
                        "${preset.label} is now the preferred provider. Choose a model in its setup screen, Sir."
                    } else {
                        "${preset.label} model ${updated.model} is now the preferred reasoning route, Sir."
                    },
                    display = "PROVIDER MESH // DEFAULT ROUTE UPDATED\nPROVIDER // ${preset.label.uppercase(Locale.US)}\nMODEL // ${updated.model.ifBlank { "SELECTION REQUIRED" }}",
                    intent = "provider_mesh/activate",
                    memorySummary = memorySummary,
                    decision = "activate_provider_route"
                )
            }
            ProviderMeshCommand.Status -> {
                val registry = store.load()
                val configured = registry.profiles.filter(ProviderMeshProfile::isConfigured)
                val online = configured.count { it.lastStatusCode in 200..299 }
                response(
                    spoken = "The universal provider mesh has ${configured.size} configured routes, with $online verified online, Sir.",
                    display = buildString {
                        appendLine("UNIVERSAL PROVIDER MESH")
                        appendLine("CONFIGURED // ${configured.size}/${ProviderMeshCatalog.presets.size}")
                        appendLine("VERIFIED ONLINE // $online")
                        appendLine("DEFAULT AI // ${ProviderMeshCatalog.byId(registry.defaultAiProviderId)?.label ?: "NOT SELECTED"}")
                        configured.forEach { profile -> appendLine("${ProviderMeshCatalog.byId(profile.id)?.label ?: profile.id} // ${profile.healthLabel()} // ${profile.model.ifBlank { "NO MODEL" }}") }
                    }.trim(),
                    intent = "provider_mesh/status",
                    memorySummary = memorySummary,
                    decision = "report_provider_mesh_status"
                )
            }
        }
    }

    private fun response(spoken: String, display: String, intent: String, memorySummary: String, decision: String): BrainResponse = BrainResponse(
        spoken = spoken,
        display = display,
        intent = intent,
        confidence = 1f,
        mode = BrainMode.EXECUTING,
        trace = listOf("provider_mesh=universal", "credentials=android_keystore_aes_gcm", "action_claims=verified_only"),
        memory = memorySummary,
        thoughts = listOf("Provider credentials remain encrypted locally and are never exposed to HELIX."),
        entities = listOf("workspace=provider_mesh"),
        decision = decision,
        action = BrainAction()
    )
}
