package com.seongja.jarvis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.URL
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs
import kotlin.math.roundToInt

/** Performs small HTTPS reachability probes away from the UI thread. */
class NetworkHealthMonitor(context: Context) {
    data class Snapshot(
        val connected: Boolean = false,
        val latencyMs: Long = -1L,
        val jitterMs: Long = 0L,
        val packetLossPercent: Int = 100,
        val downlinkMbps: Double = 0.0,
        val uplinkMbps: Double = 0.0,
        val quality: String = "PROBING"
    )

    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "friday-network-health").apply { isDaemon = true }
        }
    private val latencies = ArrayDeque<Long>()
    private val outcomes = ArrayDeque<Boolean>()

    @Volatile
    private var latest = Snapshot()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        executor.scheduleWithFixedDelay(::probeSafely, 0L, 8L, TimeUnit.SECONDS)
    }

    fun snapshot(): Snapshot = latest

    fun close() {
        started = false
        executor.shutdownNow()
    }

    private fun probeSafely() {
        if (!started) return
        val link = readLink()
        if (!link.connected) {
            recordOutcome(success = false, latencyMs = null)
            latest = link.copy(
                latencyMs = -1L,
                jitterMs = jitter(),
                packetLossPercent = packetLoss(),
                quality = "OFFLINE"
            )
            return
        }

        val startedAt = SystemClock.elapsedRealtime()
        val success = runCatching {
            val connection = (URL(PROBE_URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Friday-Android/0.9.22")
            }
            try {
                connection.responseCode in 200..399
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

        val latency = if (success) {
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
        } else {
            null
        }
        recordOutcome(success, latency)
        val measuredLatency = latency ?: latencies.lastOrNull() ?: -1L
        val measuredJitter = jitter()
        val measuredLoss = packetLoss()
        latest = link.copy(
            latencyMs = measuredLatency,
            jitterMs = measuredJitter,
            packetLossPercent = measuredLoss,
            quality = qualityLabel(
                connected = true,
                latencyMs = measuredLatency,
                jitterMs = measuredJitter,
                packetLossPercent = measuredLoss
            )
        )
    }

    private fun readLink(): Snapshot {
        val network = connectivity.activeNetwork ?: return Snapshot(quality = "OFFLINE")
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return Snapshot(quality = "OFFLINE")
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return Snapshot(
            connected = connected,
            downlinkMbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0) / 1_000.0,
            uplinkMbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0) / 1_000.0,
            quality = if (connected) "PROBING" else "OFFLINE"
        )
    }

    @Synchronized
    private fun recordOutcome(success: Boolean, latencyMs: Long?) {
        outcomes.addLast(success)
        while (outcomes.size > HISTORY_SIZE) outcomes.removeFirst()
        if (latencyMs != null) {
            latencies.addLast(latencyMs)
            while (latencies.size > HISTORY_SIZE) latencies.removeFirst()
        }
    }

    @Synchronized
    private fun jitter(): Long {
        if (latencies.size < 2) return 0L
        val values = latencies.toList()
        val deltas = values.zipWithNext { first, second -> abs(second - first).toDouble() }
        return deltas.average().roundToInt().toLong()
    }

    @Synchronized
    private fun packetLoss(): Int {
        if (outcomes.isEmpty()) return 0
        val failed = outcomes.count { !it }
        return ((failed * 100.0) / outcomes.size).roundToInt().coerceIn(0, 100)
    }

    private fun qualityLabel(
        connected: Boolean,
        latencyMs: Long,
        jitterMs: Long,
        packetLossPercent: Int
    ): String = when {
        !connected -> "OFFLINE"
        latencyMs < 0L -> "PROBING"
        packetLossPercent >= 25 || latencyMs > 180L || jitterMs > 80L -> "UNSTABLE"
        packetLossPercent >= 10 || latencyMs > 100L || jitterMs > 45L -> "DEGRADED"
        latencyMs > 50L || jitterMs > 20L -> "STABLE"
        else -> "OPTIMAL"
    }

    companion object {
        private const val PROBE_URL = "https://www.gstatic.com/generate_204"
        private const val HISTORY_SIZE = 10
    }
}
