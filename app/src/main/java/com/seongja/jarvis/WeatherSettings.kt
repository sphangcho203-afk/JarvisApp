package com.seongja.jarvis

import java.util.Locale

/** Encrypted WeatherAPI.com configuration and connection health. */
data class WeatherSettings(
    val apiKey: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val enabled: Boolean = true,
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val successes: Int = 0,
    val failures: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastError: String = ""
) {
    fun hasValidCoordinates(): Boolean =
        latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun isConfigured(): Boolean =
        enabled && apiKey.isNotBlank() && hasValidCoordinates()

    fun query(): String = String.format(
        Locale.US,
        "%.6f,%.6f",
        requireNotNull(latitude),
        requireNotNull(longitude)
    )

    fun healthLabel(): String = when {
        !enabled -> "DISABLED"
        apiKey.isBlank() -> "API KEY REQUIRED"
        !hasValidCoordinates() -> "LOCATION REQUIRED"
        lastStatusCode in 200..299 -> "ONLINE ${lastLatencyMs}ms"
        lastError.isNotBlank() ->
            "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"} // ${lastError.take(120)}"
        else -> "READY"
    }

    companion object {
        const val PROVIDER = "WeatherAPI.com"
        const val FORECAST_ENDPOINT = "https://api.weatherapi.com/v1/forecast.json"
        const val DEFAULT_REFRESH_MINUTES = 15
        const val MIN_REFRESH_MINUTES = 10
        const val MAX_REFRESH_MINUTES = 60
    }
}
