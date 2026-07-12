package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * Reads device state from Android's live system broadcasts and network capabilities.
 * ACTION_BATTERY_CHANGED is preferred over vendor-specific battery properties because
 * it exposes the same level/scale values Android uses for the status bar.
 */
class DeviceTelemetry(private val context: Context) {

    data class BatterySnapshot(
        val percent: Int,
        val charging: Boolean,
        val temperatureC: Float?
    ) {
        fun spoken(): String {
            val chargeText = if (charging) " and charging" else ""
            return "Battery is at $percent percent$chargeText."
        }
    }

    data class NetworkSnapshot(
        val connected: Boolean,
        val validated: Boolean,
        val transport: String
    ) {
        val label: String
            get() = when {
                !connected -> "OFFLINE"
                validated -> transport
                else -> "$transport?"
            }

        fun spoken(): String = when {
            !connected -> "No active internet connection is available."
            validated -> "Internet is online through ${transport.lowercase()}."
            else -> "A ${transport.lowercase()} network is connected, but Android has not validated internet access."
        }
    }

    fun battery(): BatterySnapshot {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentFromBroadcast = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            -1
        }

        val fallback = runCatching {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val rawTemperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val temperature = rawTemperature.takeIf { it != Int.MIN_VALUE }?.div(10f)

        return BatterySnapshot(
            percent = when {
                percentFromBroadcast >= 0 -> percentFromBroadcast
                fallback in 0..100 -> fallback
                else -> 0
            },
            charging = charging,
            temperatureC = temperature
        )
    }

    fun network(): NetworkSnapshot {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkSnapshot(false, false, "OFFLINE")
        val network = manager.activeNetwork ?: return NetworkSnapshot(false, false, "OFFLINE")
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return NetworkSnapshot(false, false, "OFFLINE")

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BT"
            else -> "ONLINE"
        }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return NetworkSnapshot(true, validated, transport)
    }
}
