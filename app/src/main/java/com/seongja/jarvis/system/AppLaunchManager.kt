package com.seongja.jarvis.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class AppLaunchManager(private val context: Context) {
    private val knownPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "discord" to "com.discord",
        "telegram" to "org.telegram.messenger",
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "play store" to "com.android.vending"
    )

    fun openKnownApp(name: String): Boolean {
        val normalized = name.lowercase()
        val packageName = knownPackages.entries.firstOrNull { normalized.contains(it.key) }?.value ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    fun openSettings(): Boolean {
        return openIntent(Intent(Settings.ACTION_SETTINGS))
    }

    fun openWifiSettings(): Boolean = openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))

    fun openBluetoothSettings(): Boolean = openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openBatterySettings(): Boolean = openIntent(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

    fun openUrl(url: String): Boolean {
        val safeUrl = when {
            url.startsWith("https://") || url.startsWith("http://") -> url
            "." in url && !url.contains(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=" + Uri.encode(url)
        }
        return openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
    }

    fun searchWeb(query: String): Boolean = openUrl("https://www.google.com/search?q=" + Uri.encode(query))

    private fun openIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
