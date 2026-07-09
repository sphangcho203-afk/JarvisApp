package com.seongja.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandResult(
    val response: String,
    val keepListening: Boolean = true,
    val mode: String = "ONLINE",
    val signal: String = "LOCAL",
    val commandCount: Int = 0
)

class SystemActions(private val context: Context) {
    private val appAliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "settings" to "android.settings.SETTINGS",
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "discord" to "com.discord",
        "spotify" to "com.spotify.music",
        "gmail" to "com.google.android.gm",
        "play store" to "com.android.vending",
        "calculator" to "com.google.android.calculator",
        "camera" to "com.android.camera",
        "clock" to "com.google.android.deskclock"
    )

    fun openSettings(): Boolean {
        return runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun openNotificationSettings(): Boolean {
        return runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun openApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun openKnownApp(alias: String): Boolean {
        val normalized = alias.lowercase(Locale.getDefault()).trim()
        if (normalized == "settings") return openSettings()
        val packageName = appAliases[normalized] ?: return false
        return openApp(packageName)
    }

    fun knownAppsLine(): String {
        return appAliases.keys.sorted().joinToString(", ")
    }

    fun searchWeb(query: String): Boolean {
        if (query.isBlank()) return false
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }
            .recoverCatching {
                val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                context.startActivity(browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
    }

    fun openUrl(url: String): Boolean {
        val safeUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "https://$url"
        }
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun currentClockLine(): String {
        return SimpleDateFormat("h:mm a, EEEE", Locale.getDefault()).format(Date())
    }

    fun batteryLine(): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugLine = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "not plugged"
        }
        return if (percent >= 0) {
            "Battery is $percent percent, ${if (charging) "charging" else "discharging"}, $plugLine."
        } else {
            "Battery telemetry is unavailable. Android is hiding the battery like state secrets."
        }
    }
}
