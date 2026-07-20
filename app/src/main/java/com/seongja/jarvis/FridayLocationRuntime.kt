package com.seongja.jarvis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A privacy-limited device-position snapshot. Exact street addresses are never exposed. */
data class FridayLocationSnapshot(
    val available: Boolean = false,
    val acquiring: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyM: Float = 0f,
    val altitudeM: Double = 0.0,
    val provider: String = "",
    val placeName: String = "",
    val updatedAtMs: Long = 0L,
    val error: String = ""
)

class FridayLocationRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "friday-location-runtime").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private var cancellation: CancellationSignal? = null
    private var legacyListener: LocationListener? = null

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun locate(callback: (FridayLocationSnapshot) -> Unit) {
        if (closed.get()) return
        if (!hasPermission()) {
            callback(FridayLocationSnapshot(error = "LOCATION PERMISSION REQUIRED"))
            return
        }
        callback(FridayLocationSnapshot(acquiring = true))
        cancelActiveRequest()

        val bestProvider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        val timeout = Runnable {
            if (closed.get()) return@Runnable
            val fallback = lastKnownLocation()
            if (fallback != null) deliver(fallback, callback)
            else callback(FridayLocationSnapshot(error = "DEVICE LOCATION UNAVAILABLE"))
            cancelActiveRequest()
        }
        main.postDelayed(timeout, LOCATION_TIMEOUT_MS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal().also { cancellation = it }
            runCatching {
                manager.getCurrentLocation(bestProvider, signal, executor) { location ->
                    main.removeCallbacks(timeout)
                    if (location != null) deliver(location, callback)
                    else lastKnownLocation()?.let { deliver(it, callback) }
                        ?: callback(FridayLocationSnapshot(error = "DEVICE LOCATION UNAVAILABLE"))
                }
            }.onFailure {
                main.removeCallbacks(timeout)
                lastKnownLocation()?.let { location -> deliver(location, callback) }
                    ?: callback(FridayLocationSnapshot(error = it.message ?: "LOCATION REQUEST FAILED"))
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    main.removeCallbacks(timeout)
                    deliver(location, callback)
                    cancelActiveRequest()
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }.also { legacyListener = it }
            @Suppress("DEPRECATION")
            runCatching { manager.requestSingleUpdate(bestProvider, listener, Looper.getMainLooper()) }
                .onFailure {
                    main.removeCallbacks(timeout)
                    lastKnownLocation()?.let { location -> deliver(location, callback) }
                        ?: callback(FridayLocationSnapshot(error = it.message ?: "LOCATION REQUEST FAILED"))
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .minByOrNull { it.accuracy.takeIf { value -> value > 0f } ?: Float.MAX_VALUE }
    }

    private fun deliver(location: Location, callback: (FridayLocationSnapshot) -> Unit) {
        executor.execute {
            val place = reverseGeocode(location)
            val snapshot = FridayLocationSnapshot(
                available = true,
                acquiring = false,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyM = location.accuracy.coerceAtLeast(0f),
                altitudeM = if (location.hasAltitude()) location.altitude else 0.0,
                provider = location.provider.orEmpty().uppercase(Locale.US),
                placeName = place,
                updatedAtMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            main.post { if (!closed.get()) callback(snapshot) }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): String = runCatching {
        if (!Geocoder.isPresent()) return@runCatching "VERIFIED DEVICE POSITION"
        val address = Geocoder(appContext, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?: return@runCatching "VERIFIED DEVICE POSITION"
        listOf(address.locality, address.subAdminArea, address.adminArea, address.countryName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .take(3)
            .joinToString(", ")
            .ifBlank { "VERIFIED DEVICE POSITION" }
    }.getOrDefault("VERIFIED DEVICE POSITION")

    private fun cancelActiveRequest() {
        runCatching { cancellation?.cancel() }
        cancellation = null
        legacyListener?.let { listener -> runCatching { manager.removeUpdates(listener) } }
        legacyListener = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelActiveRequest()
        main.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 12_000L
    }
}
