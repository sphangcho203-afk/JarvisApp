package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

class ActionRouter(private val context: Context) {
    private val appPackages = linkedMapOf(
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

    fun execute(action: BrainAction): Boolean = when (action.type) {
        ActionType.NONE -> false
        ActionType.OPEN_SETTINGS -> openIntent(Intent(Settings.ACTION_SETTINGS))
        ActionType.OPEN_APP -> openApp(action.payload)
        ActionType.OPEN_URL, ActionType.WEB_SEARCH -> false
    }

    private fun openApp(rawName: String): Boolean {
        val normalized = rawName.trim().lowercase(Locale.US)
        val canonical = appPackages.keys.firstOrNull { key ->
            val keyLower = key.lowercase(Locale.US)
            normalized == keyLower || normalized.contains(keyLower) || keyLower.contains(normalized)
        } ?: return false

        for (packageName in appPackages[canonical].orEmpty()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        }
        return false
    }

    private fun openIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
