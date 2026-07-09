package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class ActionRouter(private val context: Context) {
    private val appPackages = mapOf(
        "YouTube" to listOf("com.google.android.youtube"),
        "Chrome" to listOf("com.android.chrome", "com.google.android.googlequicksearchbox"),
        "Spotify" to listOf("com.spotify.music"),
        "Discord" to listOf("com.discord"),
        "Telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        "WhatsApp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "Gmail" to listOf("com.google.android.gm"),
        "Play Store" to listOf("com.android.vending"),
        "Camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
        "Files" to listOf("com.google.android.apps.nbu.files", "com.android.documentsui"),
        "Clock" to listOf("com.google.android.deskclock"),
        "Calculator" to listOf("com.google.android.calculator", "com.android.calculator2")
    )

    fun execute(action: BrainAction): Boolean {
        return when (action.type) {
            ActionType.NONE -> false
            ActionType.OPEN_SETTINGS -> openIntent(Intent(Settings.ACTION_SETTINGS))
            ActionType.OPEN_URL -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(action.payload)))
            ActionType.WEB_SEARCH -> openUrl("https://www.google.com/search?q=${Uri.encode(action.payload)}")
            ActionType.OPEN_APP -> openApp(action.payload)
        }
    }

    private fun openApp(name: String): Boolean {
        val packages = appPackages[name].orEmpty()
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return true
            }
        }
        return openUrl("https://www.google.com/search?q=${Uri.encode(name)}")
    }

    private fun openUrl(url: String): Boolean = openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
