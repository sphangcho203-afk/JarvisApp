package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherHour(
    val epochSeconds: Long,
    val localTime: String,
    val tempC: Double,
    val conditionText: String,
    val conditionCode: Int,
    val chanceOfRain: Int,
    val precipMm: Double,
    val windKph: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("epochSeconds", epochSeconds)
        put("localTime", localTime)
        put("tempC", tempC)
        put("conditionText", conditionText)
        put("conditionCode", conditionCode)
        put("chanceOfRain", chanceOfRain)
        put("precipMm", precipMm)
        put("windKph", windKph)
    }

    companion object {
        fun fromJson(root: JSONObject): WeatherHour = WeatherHour(
            epochSeconds = root.optLong("epochSeconds"),
            localTime = root.optString("localTime"),
            tempC = root.optDouble("tempC"),
            conditionText = root.optString("conditionText"),
            conditionCode = root.optInt("conditionCode"),
            chanceOfRain = root.optInt("chanceOfRain").coerceIn(0, 100),
            precipMm = root.optDouble("precipMm"),
            windKph = root.optDouble("windKph")
        )
    }
}

data class WeatherAlert(
    val headline: String,
    val event: String,
    val severity: String,
    val urgency: String,
    val expires: String,
    val instruction: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("headline", headline)
        put("event", event)
        put("severity", severity)
        put("urgency", urgency)
        put("expires", expires)
        put("instruction", instruction)
    }

    companion object {
        fun fromJson(root: JSONObject): WeatherAlert = WeatherAlert(
            headline = root.optString("headline"),
            event = root.optString("event"),
            severity = root.optString("severity"),
            urgency = root.optString("urgency"),
            expires = root.optString("expires"),
            instruction = root.optString("instruction")
        )
    }
}

data class WeatherSnapshot(
    val fetchedAtMs: Long,
    val locationName: String,
    val region: String,
    val country: String,
    val localTime: String,
    val timezoneId: String,
    val lastUpdatedEpoch: Long,
    val tempC: Double,
    val feelsLikeC: Double,
    val conditionText: String,
    val conditionCode: Int,
    val isDay: Boolean,
    val windKph: Double,
    val windDirection: String,
    val gustKph: Double,
    val humidity: Int,
    val cloudPercent: Int,
    val precipMm: Double,
    val pressureMb: Double,
    val visibilityKm: Double,
    val uv: Double,
    val todayMinC: Double,
    val todayMaxC: Double,
    val todayRainChance: Int,
    val sunrise: String,
    val sunset: String,
    val upcomingHours: List<WeatherHour>,
    val alerts: List<WeatherAlert>
) {
    fun ageMs(nowMs: Long = System.currentTimeMillis()): Long =
        (nowMs - fetchedAtMs).coerceAtLeast(0L)

    fun isFresh(maxAgeMs: Long): Boolean = ageMs() <= maxAgeMs

    fun nextRainHour(minChance: Int = 55): WeatherHour? =
        upcomingHours.firstOrNull {
            it.chanceOfRain >= minChance || it.precipMm >= 0.2
        }

    fun maximumRainChance(hours: Int = 8): Int =
        upcomingHours.take(hours).maxOfOrNull { it.chanceOfRain } ?: todayRainChance

    fun locationLabel(): String = listOf(locationName, region)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .ifBlank { country.ifBlank { "configured location" } }

    fun icon(): String {
        val lower = conditionText.lowercase(Locale.US)
        return when {
            alerts.isNotEmpty() -> "⚠️"
            "thunder" in lower || "storm" in lower -> "⛈️"
            "snow" in lower || "sleet" in lower || "blizzard" in lower -> "🌨️"
            "rain" in lower || "drizzle" in lower || precipMm > 0.0 -> "🌧️"
            "fog" in lower || "mist" in lower -> "🌫️"
            "cloud" in lower || "overcast" in lower -> "☁️"
            isDay -> "☀️"
            else -> "🌙"
        }
    }

    fun compactLabel(): String = buildString {
        append(icon())
        append(' ')
        append(tempC.roundToInt())
        append("°C // ")
        append(conditionText.uppercase(Locale.US).take(24))
        append(" // WIND ")
        append(windKph.roundToInt())
        append(" KPH // RAIN ")
        append(maximumRainChance(6))
        append('%')
    }

    fun fullSpokenSummary(title: String = "Boss"): String {
        val rain = nextRainHour()
        return buildString {
            append(title)
            append(", it is ")
            append(tempC.roundToInt())
            append(" degrees Celsius and ")
            append(conditionText.lowercase(Locale.US))
            append(" in ")
            append(locationLabel())
            append(". It feels like ")
            append(feelsLikeC.roundToInt())
            append(" degrees, with humidity at ")
            append(humidity)
            append(" percent and wind at ")
            append(windKph.roundToInt())
            append(" kilometres per hour")
            if (rain != null) {
                append(". Rain reaches ")
                append(rain.chanceOfRain)
                append(" percent around ")
                append(rain.localTime.substringAfter(' ', rain.localTime))
            } else if (todayRainChance > 20) {
                append(". Today's highest rain chance is ")
                append(todayRainChance)
                append(" percent")
            }
            append('.')
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("fetchedAtMs", fetchedAtMs)
        put("locationName", locationName)
        put("region", region)
        put("country", country)
        put("localTime", localTime)
        put("timezoneId", timezoneId)
        put("lastUpdatedEpoch", lastUpdatedEpoch)
        put("tempC", tempC)
        put("feelsLikeC", feelsLikeC)
        put("conditionText", conditionText)
        put("conditionCode", conditionCode)
        put("isDay", isDay)
        put("windKph", windKph)
        put("windDirection", windDirection)
        put("gustKph", gustKph)
        put("humidity", humidity)
        put("cloudPercent", cloudPercent)
        put("precipMm", precipMm)
        put("pressureMb", pressureMb)
        put("visibilityKm", visibilityKm)
        put("uv", uv)
        put("todayMinC", todayMinC)
        put("todayMaxC", todayMaxC)
        put("todayRainChance", todayRainChance)
        put("sunrise", sunrise)
        put("sunset", sunset)
        put("upcomingHours", JSONArray().apply {
            upcomingHours.forEach { put(it.toJson()) }
        })
        put("alerts", JSONArray().apply {
            alerts.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(root: JSONObject): WeatherSnapshot = WeatherSnapshot(
            fetchedAtMs = root.optLong("fetchedAtMs"),
            locationName = root.optString("locationName"),
            region = root.optString("region"),
            country = root.optString("country"),
            localTime = root.optString("localTime"),
            timezoneId = root.optString("timezoneId"),
            lastUpdatedEpoch = root.optLong("lastUpdatedEpoch"),
            tempC = root.optDouble("tempC"),
            feelsLikeC = root.optDouble("feelsLikeC"),
            conditionText = root.optString("conditionText"),
            conditionCode = root.optInt("conditionCode"),
            isDay = root.optBoolean("isDay"),
            windKph = root.optDouble("windKph"),
            windDirection = root.optString("windDirection"),
            gustKph = root.optDouble("gustKph"),
            humidity = root.optInt("humidity"),
            cloudPercent = root.optInt("cloudPercent"),
            precipMm = root.optDouble("precipMm"),
            pressureMb = root.optDouble("pressureMb"),
            visibilityKm = root.optDouble("visibilityKm"),
            uv = root.optDouble("uv"),
            todayMinC = root.optDouble("todayMinC"),
            todayMaxC = root.optDouble("todayMaxC"),
            todayRainChance = root.optInt("todayRainChance"),
            sunrise = root.optString("sunrise"),
            sunset = root.optString("sunset"),
            upcomingHours = root.optJSONArray("upcomingHours").toWeatherHours(),
            alerts = root.optJSONArray("alerts").toWeatherAlerts()
        )

        private fun JSONArray?.toWeatherHours(): List<WeatherHour> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optJSONObject(index)?.let { add(WeatherHour.fromJson(it)) }
                }
            }
        }

        private fun JSONArray?.toWeatherAlerts(): List<WeatherAlert> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optJSONObject(index)?.let { add(WeatherAlert.fromJson(it)) }
                }
            }
        }
    }
}
