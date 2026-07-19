package com.seongja.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Central owner gate for screens that expose credentials, integrations, or privileged controls. */
object OwnerAccessController {
    private val gateVisible = AtomicBoolean(false)

    @Volatile
    private var authenticatedUntilMs = 0L

    @Volatile
    private var pendingActivity: WeakReference<Activity>? = null

    fun protect(activity: Activity) {
        if (isSensitive(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (activity is MainActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }

    /** Returns true when a gate was required and the caller should stop its resume work. */
    fun requireAuthentication(activity: Activity): Boolean {
        if (!isSensitive(activity) || sessionIsValid()) return false

        pendingActivity = WeakReference(activity)
        if (gateVisible.compareAndSet(false, true)) {
            activity.startActivity(
                Intent(activity, OwnerAccessGateActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
        return true
    }

    fun grant() {
        authenticatedUntilMs = SystemClock.elapsedRealtime() + OWNER_SESSION_MS
        pendingActivity = null
        gateVisible.set(false)
    }

    fun deny() {
        authenticatedUntilMs = 0L
        pendingActivity?.get()?.let { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) activity.finish()
        }
        pendingActivity = null
        gateVisible.set(false)
    }

    fun clearSession() {
        authenticatedUntilMs = 0L
    }

    internal fun sessionIsValid(): Boolean =
        SystemClock.elapsedRealtime() < authenticatedUntilMs

    internal fun isSensitive(activity: Activity): Boolean = when (activity) {
        is CloudConfigActivity,
        is GmailAuthorizationActivity,
        is WaApiConfigActivity,
        is WeatherSetupActivity,
        is PermissionCenterActivity -> true
        else -> false
    }

    private const val OWNER_SESSION_MS = 2 * 60_000L
}
