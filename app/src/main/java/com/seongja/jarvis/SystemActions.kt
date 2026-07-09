package com.seongja.jarvis

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class SystemActions(private val context: Context) {

    fun openSettings(): Boolean {
        return launch(Intent(Settings.ACTION_SETTINGS))
    }

    fun openYouTube(): Boolean {
        val appIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        return if (appIntent != null) launch(appIntent) else openUrl("https://youtube.com")
    }

    fun openChrome(): Boolean {
        val appIntent = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
        return if (appIntent != null) launch(appIntent) else false
    }

    fun searchWeb(query: String): Boolean {
        val encoded = Uri.encode(query)
        return openUrl("https://www.google.com/search?q=$encoded")
    }

    fun openUrl(url: String): Boolean {
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
