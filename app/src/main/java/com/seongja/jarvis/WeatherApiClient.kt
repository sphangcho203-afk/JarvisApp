package com.seongja.jarvis

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class WeatherApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

/** Official WeatherAPI.com forecast client with redacted failures. */
class WeatherApiClient(
    private val store: SecureWeatherRegistry
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun fetch(settings: WeatherSettings = store.load()): WeatherSnapshot {
        if (!settings.isConfigured()) {
            throw WeatherApiException(0, "WeatherAPI key and valid coordinates are required.")
        }

        val started = System.currentTimeMillis()
        val url = WeatherSettings.FORECAST_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("key", settings.apiKey.trim())
            .addQueryParameter("q", settings.query())
            .addQueryParameter("days", "2")
            .addQueryParameter("alerts", "yes")
            .addQueryParameter("aqi", "no")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Jarvis-Android/0.9.20")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(raw) }.getOrElse {
                    throw WeatherApiException(response.code, "WeatherAPI returned invalid JSON.")
                }
                if (!response.isSuccessful) {
                    val message = root.optJSONObject("error")
                        ?.optString("message")
                        .orEmpty()
                        .ifBlank { "WeatherAPI HTTP ${response.code}" }
                    throw WeatherApiException(response.code, redact(message, settings.apiKey))
                }
                val snapshot = parse(root)
                store.recordSuccess(
                    latencyMs = System.currentTimeMillis() - started,
                    statusCode = response.code
                )
                return snapshot
            }
        } catch (error: WeatherApiException) {
            store.recordFailure(error.message.orEmpty(), error.statusCode)
            throw error
        } catch (error: IOException) {
            val message = "Weather network error: ${error.message ?: error.javaClass.simpleName}"
            store.recordFailure(message)
            throw WeatherApiException(0, message)
        } catch (error: Exception) {
            val message = redact(
                "Weather parsing error: ${error.message ?: error.javaClass.simpleName}",
                settings.apiKey
            )
            store.recordFailure(message)
            throw WeatherApiException(0, message)
        }
    }

    private fun parse(root: JSONObject): WeatherSnapshot {
        val location = root.getJSONObject("location")
        val current = root.getJSONObject("current")
        val condition = current.getJSONObject("condition")
        val forecastDays = root.getJSONObject("forecast").getJSONArray("forecastday")
        val today = forecastDays.getJSONObject(0)
        val day = today.getJSONObject("day")
        val astro = today.getJSONObject("astro")
        val nowEpoch = current.optLong("last_updated_epoch", System.currentTimeMillis() / 1_000L)

        val upcoming = buildList {
            for (dayIndex in 0 until forecastDays.length()) {
                val hours = forecastDays.getJSONObject(dayIndex).getJSONArray("hour")
                for (hourIndex in 0 until hours.length()) {
                    val hour = hours.getJSONObject(hourIndex)
                    val epoch = hour.optLong("time_epoch")
                    if (epoch < nowEpoch - 1_800L) continue
                    val hourCondition = hour.optJSONObject("condition") ?: JSONObject()
                    add(
                        WeatherHour(
                            epochSeconds = epoch,
                            localTime = hour.optString("time"),
                            tempC = hour.optDouble("temp_c"),
                            conditionText = hourCondition.optString("text"),
                            conditionCode = hourCondition.optInt("code"),
                            chanceOfRain = hour.optInt("chance_of_rain").coerceIn(0, 100),
                            precipMm = hour.optDouble("precip_mm"),
                            windKph = hour.optDouble("wind_kph")
                        )
                    )
                    if (size >= MAX_UPCOMING_HOURS) break
                }
                if (size >= MAX_UPCOMING_HOURS) break
            }
        }

        val alerts = buildList {
            val array = root.optJSONObject("alerts")?.optJSONArray("alert")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val alert = array.optJSONObject(index) ?: continue
                    add(
                        WeatherAlert(
                            headline = alert.optString("headline"),
                            event = alert.optString("event"),
                            severity = alert.optString("severity"),
                            urgency = alert.optString("urgency"),
                            expires = alert.optString("expires"),
                            instruction = alert.optString("instruction")
                        )
                    )
                    if (size >= MAX_ALERTS) break
                }
            }
        }

        return WeatherSnapshot(
            fetchedAtMs = System.currentTimeMillis(),
            locationName = location.optString("name"),
            region = location.optString("region"),
            country = location.optString("country"),
            localTime = location.optString("localtime"),
            timezoneId = location.optString("tz_id"),
            lastUpdatedEpoch = nowEpoch,
            tempC = current.optDouble("temp_c"),
            feelsLikeC = current.optDouble("feelslike_c"),
            conditionText = condition.optString("text", "Unknown"),
            conditionCode = condition.optInt("code"),
            isDay = current.optInt("is_day", 1) == 1,
            windKph = current.optDouble("wind_kph"),
            windDirection = current.optString("wind_dir"),
            gustKph = current.optDouble("gust_kph"),
            humidity = current.optInt("humidity").coerceIn(0, 100),
            cloudPercent = current.optInt("cloud").coerceIn(0, 100),
            precipMm = current.optDouble("precip_mm"),
            pressureMb = current.optDouble("pressure_mb"),
            visibilityKm = current.optDouble("vis_km"),
            uv = current.optDouble("uv"),
            todayMinC = day.optDouble("mintemp_c"),
            todayMaxC = day.optDouble("maxtemp_c"),
            todayRainChance = day.optInt("daily_chance_of_rain").coerceIn(0, 100),
            sunrise = astro.optString("sunrise"),
            sunset = astro.optString("sunset"),
            upcomingHours = upcoming,
            alerts = alerts
        )
    }

    private fun redact(value: String, apiKey: String): String = value
        .replace(apiKey, "[redacted]", ignoreCase = false)
        .replace(Regex("(?i)key=[^&\\s]+"), "key=[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(280)

    companion object {
        private const val MAX_UPCOMING_HOURS = 24
        private const val MAX_ALERTS = 4
    }
}
