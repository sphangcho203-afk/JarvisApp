package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One-time Android capability onboarding for Jarvis.
 *
 * This screen never claims permissions are active merely because they exist in
 * the manifest. Every status is read back from Android before being displayed.
 */
class PermissionCenterActivity : Activity() {

    private data class StatusRow(
        val status: TextView,
        val detail: TextView
    )

    private lateinit var microphoneRow: StatusRow
    private lateinit var cameraRow: StatusRow
    private lateinit var notificationRow: StatusRow
    private lateinit var systemControlRow: StatusRow
    private lateinit var appAutomationRow: StatusRow
    private lateinit var writeSettingsRow: StatusRow
    private lateinit var usageAccessRow: StatusRow
    private lateinit var notificationAccessRow: StatusRow
    private lateinit var developerOptionsRow: StatusRow
    private lateinit var overallStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(26))
            setBackgroundColor(Color.rgb(6, 12, 19))
        }

        root.addView(TextView(this).apply {
            text = "JARVIS // ANDROID PERMISSION CENTER"
            textSize = 23f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.rgb(62, 239, 225))
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Grant only the Android capabilities you want Jarvis to use. Each item is verified from the operating system. Developer Options helps testing, but it does not silently grant app permissions."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(14))
        })

        overallStatus = TextView(this).apply {
            text = "STATUS // CHECKING ANDROID"
            textSize = 13f
            setTextColor(Color.rgb(145, 205, 255))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelBackground(Color.rgb(13, 25, 37), Color.rgb(38, 105, 128))
        }
        root.addView(overallStatus, matchWidth(bottom = 14))

        microphoneRow = addCapability(
            root,
            title = "MICROPHONE",
            description = "Required for wake phrases and spoken commands.",
            buttonLabel = "REQUEST MICROPHONE + CORE PERMISSIONS",
            action = ::requestRuntimePermissions
        )

        cameraRow = addCapability(
            root,
            title = "CAMERA / TORCH",
            description = "Lets Jarvis control the camera flash through Android's torch API when available.",
            buttonLabel = "REQUEST CAMERA PERMISSION",
            action = ::requestRuntimePermissions
        )

        notificationRow = addCapability(
            root,
            title = "APP NOTIFICATIONS",
            description = "Allows the wake listener and long-running voice operations to show Android's required notification.",
            buttonLabel = "OPEN APP NOTIFICATION SETTINGS",
            action = ::openAppNotificationSettings
        )

        systemControlRow = addCapability(
            root,
            title = "SYSTEM CONTROL ACCESSIBILITY",
            description = "Allows verified Quick Settings actions such as torch, Wi-Fi, hotspot, mobile data, flight mode, Eye Comfort, and Do Not Disturb.",
            buttonLabel = "OPEN ACCESSIBILITY SETTINGS",
            action = ::openAccessibilitySettings
        )

        appAutomationRow = addCapability(
            root,
            title = "APP AUTOMATION + SCREEN CONTEXT",
            description = "Allows on-demand screen-tree reading, confirmed app actions, gesture capability, and screenshots on supported Android versions. It does not grant lock-screen bypass.",
            buttonLabel = "OPEN ACCESSIBILITY SETTINGS",
            action = ::openAccessibilitySettings
        )

        writeSettingsRow = addCapability(
            root,
            title = "MODIFY SYSTEM SETTINGS",
            description = "Allows brightness, rotation, and other settings that Android exposes through WRITE_SETTINGS.",
            buttonLabel = "OPEN MODIFY SYSTEM SETTINGS",
            action = ::openWriteSettings
        )

        usageAccessRow = addCapability(
            root,
            title = "USAGE ACCESS",
            description = "Allows Jarvis to identify the recently active app and improve screen/task context. It does not reveal passwords or protected fields.",
            buttonLabel = "OPEN USAGE ACCESS",
            action = ::openUsageAccess
        )

        notificationAccessRow = addCapability(
            root,
            title = "NOTIFICATION ACCESS",
            description = "Optional foundation for notification summaries and command context. The current service does not persist notification content.",
            buttonLabel = "OPEN NOTIFICATION ACCESS",
            action = ::openNotificationListenerSettings
        )

        developerOptionsRow = addCapability(
            root,
            title = "DEVELOPER OPTIONS",
            description = "Useful for ADB logs, USB debugging, and testing. It does not give Jarvis root or unrestricted system authority.",
            buttonLabel = "OPEN DEVELOPER OPTIONS",
            action = ::openDeveloperOptions
        )

        root.addView(Button(this).apply {
            text = "REFRESH ANDROID STATUS"
            setOnClickListener { refreshStatus() }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "FINISH PERMISSION SETUP"
            setOnClickListener {
                markOnboardingComplete(this@PermissionCenterActivity)
                finish()
            }
        }, matchWidth(bottom = 8))

        root.addView(TextView(this).apply {
            text = "After API setup is complete, the HELIX setup button becomes ANDROID and reopens this screen. Android may revoke permissions or accessibility services at any time, so Jarvis must continue verifying state before every sensitive action."
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, dp(8), 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun addCapability(
        parent: LinearLayout,
        title: String,
        description: String,
        buttonLabel: String,
        action: () -> Unit
    ): StatusRow {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(Color.rgb(11, 22, 33), Color.rgb(35, 94, 113))
        }
        panel.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.rgb(73, 226, 211))
        })
        val detail = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(7))
        }
        panel.addView(detail)
        val status = TextView(this).apply {
            text = "STATUS // CHECKING"
            textSize = 12f
            setTextColor(Color.rgb(150, 210, 255))
            setPadding(0, 0, 0, dp(7))
        }
        panel.addView(status)
        panel.addView(Button(this).apply {
            text = buttonLabel
            setOnClickListener { action() }
        }, matchWidth())
        parent.addView(panel, matchWidth(bottom = 12))
        return StatusRow(status, detail)
    }

    private fun refreshStatus() {
        val mic = hasPermission(Manifest.permission.RECORD_AUDIO)
        val camera = hasPermission(Manifest.permission.CAMERA)
        val notifications = hasNotificationPermission()
        val systemControl = isAccessibilityEnabled(
            ComponentName(this, com.jarvis.core.device.JarvisSystemControlService::class.java)
        )
        val appAutomation = isAccessibilityEnabled(
            ComponentName(this, com.jarvis.core.device.JarvisAppAutomationService::class.java)
        )
        val writeSettings = Settings.System.canWrite(this)
        val usageAccess = hasUsageAccess()
        val notificationAccess = hasNotificationListenerAccess()
        val developerOptions = Settings.Global.getInt(
            contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1

        setStatus(microphoneRow, mic)
        setStatus(cameraRow, camera)
        setStatus(notificationRow, notifications)
        setStatus(systemControlRow, systemControl)
        setStatus(appAutomationRow, appAutomation)
        setStatus(writeSettingsRow, writeSettings)
        setStatus(usageAccessRow, usageAccess)
        setStatus(notificationAccessRow, notificationAccess)
        setStatus(developerOptionsRow, developerOptions, enabledWord = "ENABLED", disabledWord = "DISABLED")

        val core = listOf(mic, notifications, systemControl, appAutomation)
        val optional = listOf(camera, writeSettings, usageAccess, notificationAccess)
        overallStatus.text = buildString {
            append("CORE ")
            append(core.count { it })
            append('/')
            append(core.size)
            append(" // OPTIONAL ")
            append(optional.count { it })
            append('/')
            append(optional.size)
            append(" // DEV OPTIONS ")
            append(if (developerOptions) "ON" else "OFF")
        }
    }

    private fun setStatus(
        row: StatusRow,
        enabled: Boolean,
        enabledWord: String = "GRANTED",
        disabledWord: String = "NOT GRANTED"
    ) {
        row.status.text = "STATUS // ${if (enabled) enabledWord else disabledWord}"
        row.status.setTextColor(
            if (enabled) Color.rgb(93, 238, 176) else Color.rgb(255, 171, 95)
        )
    }

    private fun requestRuntimePermissions() {
        val required = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) required += Manifest.permission.RECORD_AUDIO
        if (!hasPermission(Manifest.permission.CAMERA)) required += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            required += Manifest.permission.POST_NOTIFICATIONS
        }
        if (required.isEmpty()) {
            refreshStatus()
            return
        }
        requestPermissions(required.toTypedArray(), REQUEST_RUNTIME_PERMISSIONS)
    }

    private fun openAccessibilitySettings() = openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openWriteSettings() = openSettings(
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")
        )
    )

    private fun openUsageAccess() = openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun openNotificationListenerSettings() =
        openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun openDeveloperOptions() =
        openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))

    private fun openAppNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestRuntimePermissions()
            return
        }
        openSettings(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(this, JarvisNotificationListenerService::class.java)
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    private fun isAccessibilityEnabled(component: ComponentName): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(12).toFloat()
        }

    private fun matchWidth(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_NAME = "jarvis_permission_onboarding"
        private const val KEY_COMPLETED = "completed"
        private const val REQUEST_RUNTIME_PERMISSIONS = 2107

        fun isOnboardingComplete(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)

        fun markOnboardingComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED, true)
                .apply()
        }
    }
}
