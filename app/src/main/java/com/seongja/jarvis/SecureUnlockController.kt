package com.seongja.jarvis

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent

/**
 * Requests Android's official device-credential UI.
 *
 * This controller never reads, stores, receives, or replays a PIN, password,
 * pattern, biometric template, or lock-screen coordinate path.
 */
class SecureUnlockController(context: Context) {
    private val appContext = context.applicationContext
    private val keyguardManager =
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    enum class RequestResult {
        PROMPT_OPENED,
        DEVICE_NOT_SECURE,
        PROMPT_UNAVAILABLE,
        FAILED
    }

    fun requestAuthentication(): RequestResult {
        if (!keyguardManager.isDeviceSecure) {
            return RequestResult.DEVICE_NOT_SECURE
        }

        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Unlock device",
            "Authenticate with Android to continue."
        ) ?: return RequestResult.PROMPT_UNAVAILABLE

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            RequestResult.PROMPT_OPENED
        }.getOrDefault(RequestResult.FAILED)
    }

    fun isDeviceLocked(): Boolean = keyguardManager.isDeviceLocked
}
