package com.seongja.jarvis

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class WeatherAnswer(
    val spoken: String,
    val display: String,
    val intent: String
)

/** Cached, deterministic weather reasoning built on verified WeatherAPI data. */
class WeatherIntelligence(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureWeatherRegistry(appContext)
    private val client = WeatherApiClient(store)
    private val cache = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = store.load().isConfigured()

    fun settings(): WeatherSettings = store.load()

    @Synchronized
    fun cached(): WeatherSnapshot? {
        val raw = cache.getString(KEY_SNAPSHOT, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching { WeatherSnapshot.fromJson(JSONObject(raw)) }.getOrNull()
    }

    @Synchronized
    fun refresh(force: Boolean = false): WeatherSnapshot {
        val settings = store.load()
        if (!settings.isConfigured()) {
            throw WeatherApiException(0, "WeatherAPI is not configured.")
        }
        val maxAge = settings.refreshMinutes.coerceIn(
            WeatherSettings.MIN_REFRESH_MINUTES,
            WeatherSettings.MAX_REFRESH_MINUTES
        ) * 60_000L
        cached()?.takeIf { !force && it.isFresh(maxAge) }?.let { return it }
        val snapshot = client.fetch(settings)
        cache.edit().putString(KEY_SNAPSHOT, snapshot.toJson().toString()).apply()
        return snapshot
    }

    fun test(settings: WeatherSettings): WeatherSnapshot {
        store.save(settings)
        val snapshot = client.fetch(settings)
        cache.edit().putString(KEY_SNAPSHOT, snapshot.toJson().toString()).apply()
        return snapshot
    }

    fun answer(rawInput: String): WeatherAnswer? {
        val input = rawInput.trim().lowercase(Locale.US)
        if (!WEATHER_INTENT.containsMatchIn(input)) return null
        if (!isConfigured()) {
            return WeatherAnswer(
                spoken = "WeatherAPI is not configured yet, Boss. Open weather setup and enter the key, latitude, and longitude.",
                display = "WEATHER CORE // CONFIGURATION REQUIRED\nFIELDS // API KEY + LATITUDE + LONGITUDE",
                intent = "weather/configuration_required"
            )
        }
        val snapshot = cached()
            ?: return WeatherAnswer(
                spoken = "The weather core is connecting, Sir. Give it a moment, then ask again.",
                display = "WEATHER CORE // ACQUIRING VERIFIED CONDITIONS",
                intent = "weather/acquiring"
            )

        return when {
            RAIN_INTENT.containsMatchIn(input) -> rainAnswer(snapshot)
            WIND_INTENT.containsMatchIn(input) -> WeatherAnswer(
                spoken = "Boss, wind is ${snapshot.windKph.roundToInt()} kilometres per hour from ${snapshot.windDirection.ifBlank { "an unknown direction" }}, with gusts up to ${snapshot.gustKph.roundToInt()} kilometres per hour.",
                display = "WIND // ${snapshot.windKph.roundToInt()} KPH ${snapshot.windDirection}\nGUSTS // ${snapshot.gustKph.roundToInt()} KPH\nLOCATION // ${snapshot.locationLabel()}",
                intent = "weather/wind"
            )
            HUMIDITY_INTENT.containsMatchIn(input) -> WeatherAnswer(
                spoken = "Humidity is ${snapshot.humidity} percent, Sir. The temperature is ${snapshot.tempC.roundToInt()} degrees, but it feels like ${snapshot.feelsLikeC.roundToInt()}.",
                display = "HUMIDITY // ${snapshot.humidity}%\nTEMPERATURE // ${snapshot.tempC.roundToInt()}°C\nFEELS LIKE // ${snapshot.feelsLikeC.roundToInt()}°C",
                intent = "weather/humidity"
            )
            SUN_INTENT.containsMatchIn(input) -> WeatherAnswer(
                spoken = "Sunrise is ${snapshot.sunrise} and sunset is ${snapshot.sunset}, Boss.",
                display = "SUNRISE // ${snapshot.sunrise}\nSUNSET // ${snapshot.sunset}\nLOCATION // ${snapshot.locationLabel()}",
                intent = "weather/astronomy"
            )
            TEMPERATURE_INTENT.containsMatchIn(input) -> WeatherAnswer(
                spoken = "It is ${snapshot.tempC.roundToInt()} degrees Celsius, feeling like ${snapshot.feelsLikeC.roundToInt()}, with a high of ${snapshot.todayMaxC.roundToInt()} and a low of ${snapshot.todayMinC.roundToInt()}, Boss.",
                display = "TEMPERATURE // ${snapshot.tempC.roundToInt()}°C\nFEELS LIKE // ${snapshot.feelsLikeC.roundToInt()}°C\nTODAY // ${snapshot.todayMinC.roundToInt()}°C TO ${snapshot.todayMaxC.roundToInt()}°C",
                intent = "weather/temperature"
            )
            else -> WeatherAnswer(
                spoken = snapshot.fullSpokenSummary(if (input.hashCode() and 1 == 0) "Boss" else "Sir"),
                display = buildDisplay(snapshot),
                intent = "weather/current_forecast"
            )
        }
    }

    fun bridgeJson(): String {
        val settings = store.load()
        val snapshot = cached()
        return JSONObject().apply {
            put("configured", settings.isConfigured())
            put("status", settings.healthLabel())
            put("fresh", snapshot?.isFresh(MAX_HUD_AGE_MS) == true)
            if (snapshot != null) {
                put("location", snapshot.locationLabel())
                put("tempC", snapshot.tempC)
                put("feelsLikeC", snapshot.feelsLikeC)
                put("condition", snapshot.conditionText)
                put("conditionCode", snapshot.conditionCode)
                put("icon", snapshot.icon())
                put("isDay", snapshot.isDay)
                put("windKph", snapshot.windKph)
                put("windDirection", snapshot.windDirection)
                put("gustKph", snapshot.gustKph)
                put("humidity", snapshot.humidity)
                put("cloudPercent", snapshot.cloudPercent)
                put("precipMm", snapshot.precipMm)
                put("rainChance", snapshot.maximumRainChance(6))
                put("todayMinC", snapshot.todayMinC)
                put("todayMaxC", snapshot.todayMaxC)
                put("updatedAtMs", snapshot.fetchedAtMs)
                put("alert", snapshot.alerts.firstOrNull()?.headline.orEmpty())
            }
        }.toString()
    }

    fun advisory(snapshot: WeatherSnapshot): String? {
        snapshot.alerts.firstOrNull()?.let { alert ->
            return "Weather alert, Sir: ${alert.headline.ifBlank { alert.event }}. ${alert.instruction.take(180)}"
        }
        val lower = snapshot.conditionText.lowercase(Locale.US)
        val rain = snapshot.nextRainHour(65)
        return when {
            "thunder" in lower || "storm" in lower ->
                "Storm conditions are active near ${snapshot.locationLabel()}, Boss. Avoid exposed areas and keep an eye on official alerts."
            snapshot.feelsLikeC >= 40.0 || snapshot.tempC >= 37.0 ->
                "Heat warning, Sir. It feels like ${snapshot.feelsLikeC.roundToInt()} degrees outside. Hydration and shade would be sensible."
            snapshot.windKph >= 45.0 || snapshot.gustKph >= 60.0 ->
                "Strong wind warning, Boss. Winds are ${snapshot.windKph.roundToInt()} kilometres per hour with gusts near ${snapshot.gustKph.roundToInt()}."
            rain != null ->
                "Rain is likely around ${rain.localTime.substringAfter(' ', rain.localTime)}, Boss, with a ${rain.chanceOfRain} percent chance. An umbrella would be strategically sound."
            snapshot.precipMm > 0.0 || "rain" in lower || "drizzle" in lower ->
                "It is raining now, Sir. Current precipitation is ${String.format(Locale.US, "%.1f", snapshot.precipMm)} millimetres."
            else -> null
        }
    }

    fun advisoryFingerprint(snapshot: WeatherSnapshot): String = buildString {
        append(snapshot.alerts.firstOrNull()?.headline.orEmpty())
        append('|')
        append(snapshot.conditionCode)
        append('|')
        append(snapshot.nextRainHour(65)?.epochSeconds ?: 0L)
        append('|')
        append((snapshot.feelsLikeC / 3.0).roundToInt())
        append('|')
        append((snapshot.windKph / 10.0).roundToInt())
    }

    private fun rainAnswer(snapshot: WeatherSnapshot): WeatherAnswer {
        val rain = snapshot.nextRainHour(40)
        val chance = snapshot.maximumRainChance(8)
        val spoken = when {
            snapshot.precipMm > 0.0 ->
                "Yes, Sir. It is raining now, with ${String.format(Locale.US, "%.1f", snapshot.precipMm)} millimetres of precipitation."
            rain != null ->
                "Rain is most likely around ${rain.localTime.substringAfter(' ', rain.localTime)}, Boss, at ${rain.chanceOfRain} percent."
            chance >= 20 ->
                "There is a $chance percent rain chance over the next several hours, Sir, but no strong rain window is confirmed yet."
            else ->
                "Rain looks unlikely over the next several hours, Boss. The highest current chance is $chance percent."
        }
        return WeatherAnswer(
            spoken = spoken,
            display = "RAIN NOW // ${String.format(Locale.US, "%.1f", snapshot.precipMm)} MM\nNEXT 8H // $chance% MAX\nNEXT WINDOW // ${rain?.localTime ?: "NONE DETECTED"}",
            intent = "weather/rain"
        )
    }

    private fun buildDisplay(snapshot: WeatherSnapshot): String = buildString {
        appendLine("${snapshot.icon()} ${snapshot.conditionText.uppercase(Locale.US)} // ${snapshot.tempC.roundToInt()}°C")
        appendLine("FEELS LIKE // ${snapshot.feelsLikeC.roundToInt()}°C")
        appendLine("TODAY // ${snapshot.todayMinC.roundToInt()}°C TO ${snapshot.todayMaxC.roundToInt()}°C")
        appendLine("HUMIDITY // ${snapshot.humidity}% // CLOUD ${snapshot.cloudPercent}%")
        appendLine("WIND // ${snapshot.windKph.roundToInt()} KPH ${snapshot.windDirection} // GUST ${snapshot.gustKph.roundToInt()}")
        appendLine("RAIN // ${snapshot.maximumRainChance(8)}% // PRECIP ${String.format(Locale.US, "%.1f", snapshot.precipMm)} MM")
        appendLine("SUNRISE // ${snapshot.sunrise} // SUNSET ${snapshot.sunset}")
        append("LOCATION // ${snapshot.locationLabel()}")
    }

    companion object {
        private const val CACHE_PREFS = "jarvis_weather_cache"
        private const val KEY_SNAPSHOT = "latest_snapshot"
        private const val MAX_HUD_AGE_MS = 90 * 60_000L
        private val WEATHER_INTENT = Regex(
            "\\b(weather|forecast|temperature|temp|rain|rainy|cloud|cloudy|wind|windy|humidity|humid|hot|heat|cold|outside|umbrella|sunrise|sunset|storm|drizzle)\\b",
            RegexOption.IGNORE_CASE
        )
        private val RAIN_INTENT = Regex("\\b(rain|rainy|umbrella|drizzle|precipitation)\\b", RegexOption.IGNORE_CASE)
        private val WIND_INTENT = Regex("\\b(wind|windy|gust)\\b", RegexOption.IGNORE_CASE)
        private val HUMIDITY_INTENT = Regex("\\b(humidity|humid)\\b", RegexOption.IGNORE_CASE)
        private val SUN_INTENT = Regex("\\b(sunrise|sunset)\\b", RegexOption.IGNORE_CASE)
        private val TEMPERATURE_INTENT = Regex("\\b(temperature|temp|hot|heat|cold|degrees)\\b", RegexOption.IGNORE_CASE)
    }
}

/** Process-level refresh scheduler and listener hub. */
object WeatherRuntime {
    private val initialized = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<(WeatherSnapshot) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jarvis-weather-core").apply { isDaemon = true }
    }

    @Volatile
    private var intelligence: WeatherIntelligence? = null

    @Volatile
    private var lastAdvisoryFingerprint: String = ""

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            intelligence = WeatherIntelligence(context.applicationContext)
        }
    }

    fun isConfigured(): Boolean = intelligence?.isConfigured() == true

    fun settings(): WeatherSettings = intelligence?.settings() ?: WeatherSettings()

    fun bridgeJson(): String = intelligence?.bridgeJson()
        ?: JSONObject().put("configured", false).put("status", "INITIALIZING").toString()

    fun answer(input: String): WeatherAnswer? = intelligence?.answer(input)

    fun cached(): WeatherSnapshot? = intelligence?.cached()

    fun addListener(listener: (WeatherSnapshot) -> Unit) {
        listeners += listener
        cached()?.let(listener)
    }

    fun removeListener(listener: (WeatherSnapshot) -> Unit) {
        listeners -= listener
    }

    fun refresh(force: Boolean = false, callback: ((Result<WeatherSnapshot>) -> Unit)? = null) {
        val core = intelligence
        if (core == null || !core.isConfigured()) {
            callback?.invoke(Result.failure(WeatherApiException(0, "WeatherAPI is not configured.")))
            return
        }
        if (!refreshing.compareAndSet(false, true)) return
        executor.execute {
            val result = runCatching { core.refresh(force) }
            refreshing.set(false)
            result.onSuccess { snapshot -> listeners.forEach { it(snapshot) } }
            callback?.invoke(result)
        }
    }

    fun consumeAdvisory(snapshot: WeatherSnapshot): String? {
        val core = intelligence ?: return null
        val advisory = core.advisory(snapshot) ?: return null
        val fingerprint = core.advisoryFingerprint(snapshot)
        if (fingerprint == lastAdvisoryFingerprint) return null
        lastAdvisoryFingerprint = fingerprint
        return advisory
    }
}
