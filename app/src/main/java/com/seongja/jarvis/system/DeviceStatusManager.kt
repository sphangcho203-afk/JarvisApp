package com.seongja.jarvis.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.seongja.jarvis.models.TelemetryState
import kotlin.math.roundToLong

class DeviceStatusManager(private val context: Context) {
    private val startTime = SystemClock.elapsedRealtime()

    fun telemetry(commandCount: Int, micReady: Boolean, permissionStatus: String): TelemetryState {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToLong().toInt() else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0

        return TelemetryState(
            batteryPercent = percent,
            charging = charging,
            network = networkLabel(),
            memoryUsedMb = usedMb,
            memoryMaxMb = maxMb,
            storageFreeGb = freeGb,
            uptimeSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000,
            commandCount = commandCount,
            microphoneReady = micReady,
            permissionStatus = permissionStatus
        )
    }

    fun batteryReport(): String {
        val t = telemetry(0, false, "OK")
        val chargingText = if (t.charging) "charging" else "not charging"
        return if (t.batteryPercent >= 0) "Battery is ${t.batteryPercent} percent and $chargingText." else "Battery telemetry is unavailable."
    }

    fun networkLabel(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "OFFLINE"
        val caps = manager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "ONLINE"
        }
    }
}
