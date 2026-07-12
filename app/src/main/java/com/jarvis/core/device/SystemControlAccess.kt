package com.jarvis.core.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object SystemControlAccess {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, JarvisSystemControlService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next()) ?: continue
            if (component == expected) return true
        }
        return false
    }

    fun openSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
