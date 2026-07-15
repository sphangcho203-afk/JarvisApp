package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.jarvis.core.device.AppLauncher
import com.jarvis.core.device.WebNavigator

/**
 * Compatibility action router used by typed brain actions.
 *
 * App execution delegates to the same dynamic registry as the deterministic
 * Android action fabric, removing the older fixed package-name list.
 */
class ActionRouter(private val context: Context) {
    private val appLauncher = AppLauncher(context.applicationContext)
    private val webNavigator = WebNavigator(context.applicationContext)
    private val unlockController = SecureUnlockController(context.applicationContext)

    fun execute(action: BrainAction): Boolean = when (action.type) {
        ActionType.NONE -> false
        ActionType.OPEN_SETTINGS -> openIntent(Intent(Settings.ACTION_SETTINGS))
        ActionType.OPEN_APP -> when (appLauncher.openApp(action.payload)) {
            is AppLauncher.LaunchResult.Opened -> true
            else -> false
        }
        ActionType.OPEN_URL -> when (webNavigator.openWebsite(action.payload)) {
            is WebNavigator.NavigationResult.Opened -> true
            else -> false
        }
        ActionType.WEB_SEARCH -> when (webNavigator.browse(action.payload)) {
            is WebNavigator.NavigationResult.Opened -> true
            else -> false
        }
        ActionType.REQUEST_DEVICE_UNLOCK -> when (unlockController.requestAuthentication()) {
            SecureUnlockController.RequestResult.PROMPT_OPENED -> true
            SecureUnlockController.RequestResult.DEVICE_NOT_SECURE,
            SecureUnlockController.RequestResult.PROMPT_UNAVAILABLE,
            SecureUnlockController.RequestResult.FAILED -> false
        }
    }

    fun isDeviceLocked(): Boolean = unlockController.isDeviceLocked()

    private fun openIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
