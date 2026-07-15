from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


root = Path(__file__).resolve().parents[1]
router = root / "app/src/main/java/com/jarvis/core/device/DeviceCommandRouter.kt"
local_agent = root / "app/src/main/java/com/jarvis/core/device/LocalAppAgent.kt"
wake = root / "app/src/main/java/com/seongja/jarvis/JarvisWakeService.kt"
manifest = root / "app/src/main/AndroidManifest.xml"
gradle = root / "app/build.gradle.kts"
main_activity = root / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"

replace_once(
    router,
    """    private val telemetry = DeviceTelemetry(appContext)\n    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager\n""",
    """    private val telemetry = DeviceTelemetry(appContext)\n    private val localAppAgent = LocalAppAgent(appContext, onDeferredResult)\n    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager\n""",
)
replace_once(
    router,
    """    fun executeDetailed(command: String): DeviceActionResult? {\n        val started = SystemClock.elapsedRealtime()\n        val normalized = normalizeNaturalCommand(command)\n        if (normalized.isBlank()) return null\n\n        systemControlCommand(normalized, started)?.let { return it }\n""",
    """    fun executeDetailed(command: String): DeviceActionResult? {\n        val started = SystemClock.elapsedRealtime()\n        val raw = command.trim()\n        if (raw.isBlank()) return null\n\n        localAppAgent.handle(raw, started)?.let { return it }\n\n        val normalized = normalizeNaturalCommand(raw)\n        if (normalized.isBlank()) return null\n        systemControlCommand(normalized, started)?.let { return it }\n""",
)
replace_once(
    router,
    "Android action fabric online. I can launch installed apps, browse and search, report time, date, battery and network state, control flashlight, media, volume, brightness and rotation, operate timers, and use the optional System Control bridge for allow-listed Quick Settings tiles.",
    "Android action fabric online. I can launch apps, browse, report device telemetry, control media and settings, compose Gmail drafts, send confirmed Gmail or WhatsApp messages, read the current screen on demand, and use the optional local wake listener.",
)

replace_once(
    local_agent,
    "import android.os.SystemClock\n",
    "import android.os.SystemClock\nimport com.seongja.jarvis.JarvisWakeService\n",
)
replace_once(
    local_agent,
    """        if (lower in STATUS_COMMANDS) return statusResult(startedAtMs)\n        if (lower in ENABLE_COMMANDS) return enableResult(startedAtMs)\n""",
    """        if (lower in WAKE_STATUS_COMMANDS) return wakeStatusResult(startedAtMs)\n        if (lower in ENABLE_WAKE_COMMANDS) return enableWakeResult(startedAtMs)\n        if (lower in DISABLE_WAKE_COMMANDS) return disableWakeResult(startedAtMs)\n        if (lower in STATUS_COMMANDS) return statusResult(startedAtMs)\n        if (lower in ENABLE_COMMANDS) return enableResult(startedAtMs)\n""",
)
replace_once(
    local_agent,
    """    private fun statusResult(startedAtMs: Long): DeviceActionResult {\n""",
    """    private fun wakeStatusResult(startedAtMs: Long): DeviceActionResult {\n        val capable = JarvisWakeService.canRun(appContext)\n        val running = JarvisWakeService.isRunning()\n        return result(\n            actionId = \"wake_listener_status\",\n            target = \"wake up jarvis\",\n            status = when {\n                running -> DeviceActionStatus.SUCCESS\n                capable -> DeviceActionStatus.USER_CONFIRMATION_REQUIRED\n                else -> DeviceActionStatus.FAILED\n            },\n            spoken = when {\n                running -> \"The local wake listener is active.\"\n                capable -> \"The local wake listener is available but stopped. Say enable wake listener.\"\n                else -> \"Android on-device speech recognition is unavailable or microphone permission is missing.\"\n            },\n            startedAtMs = startedAtMs,\n            trace = listOf(\n                \"local_wake_listener\",\n                \"capable=$capable\",\n                \"running=$running\"\n            )\n        )\n    }\n\n    private fun enableWakeResult(startedAtMs: Long): DeviceActionResult {\n        val started = JarvisWakeService.start(appContext)\n        return result(\n            actionId = \"wake_listener_enable\",\n            target = \"wake up jarvis\",\n            status = if (started) DeviceActionStatus.SUCCESS else DeviceActionStatus.FAILED,\n            spoken = if (started) {\n                \"Local wake listener enabled. A visible microphone notification will remain active.\"\n            } else {\n                \"The wake listener could not start. Android requires microphone permission and an on-device recognizer.\"\n            },\n            startedAtMs = startedAtMs,\n            trace = listOf(\n                \"local_wake_listener\",\n                if (started) \"foreground_service_started\" else \"start_failed\"\n            )\n        )\n    }\n\n    private fun disableWakeResult(startedAtMs: Long): DeviceActionResult {\n        val stopped = JarvisWakeService.stop(appContext)\n        return result(\n            actionId = \"wake_listener_disable\",\n            target = \"wake up jarvis\",\n            status = if (stopped) DeviceActionStatus.SUCCESS else DeviceActionStatus.FAILED,\n            spoken = if (stopped) \"Local wake listener stopped.\" else \"The wake listener could not be stopped.\",\n            startedAtMs = startedAtMs,\n            trace = listOf(\n                \"local_wake_listener\",\n                if (stopped) \"service_stop_requested\" else \"stop_failed\"\n            )\n        )\n    }\n\n    private fun statusResult(startedAtMs: Long): DeviceActionResult {\n""",
)
replace_once(
    local_agent,
    """        private val STATUS_COMMANDS = setOf(\n""",
    """        private val WAKE_STATUS_COMMANDS = setOf(\n            \"wake listener status\",\n            \"wake word status\",\n            \"is wake up jarvis enabled\"\n        )\n        private val ENABLE_WAKE_COMMANDS = setOf(\n            \"enable wake listener\",\n            \"enable wake word\",\n            \"start wake listener\",\n            \"always listen for wake up jarvis\"\n        )\n        private val DISABLE_WAKE_COMMANDS = setOf(\n            \"disable wake listener\",\n            \"disable wake word\",\n            \"stop wake listener\"\n        )\n        private val STATUS_COMMANDS = setOf(\n""",
)

replace_once(wake, "import android.os.Bundle\n", "import android.os.Build\nimport android.os.Bundle\n")
replace_once(wake, "import androidx.annotation.RequiresPermission\n", "")
replace_once(wake, "    @RequiresPermission(Manifest.permission.RECORD_AUDIO)\n    private fun startListeningSoon", "    private fun startListeningSoon")
replace_once(wake, "    @RequiresPermission(Manifest.permission.RECORD_AUDIO)\n    private fun startListening", "    private fun startListening")
replace_once(
    wake,
    """        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {\n            stopSelf()\n            return\n        }\n""",
    """        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||\n            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)\n        ) {\n            stopSelf()\n            return\n        }\n""",
)
replace_once(
    wake,
    """        fun canRun(context: Context): Boolean =\n            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==\n                PackageManager.PERMISSION_GRANTED &&\n                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)\n""",
    """        fun canRun(context: Context): Boolean =\n            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&\n                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==\n                PackageManager.PERMISSION_GRANTED &&\n                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)\n""",
)

replace_once(
    manifest,
    """    <uses-permission android:name=\"android.permission.WRITE_SETTINGS\" />\n""",
    """    <uses-permission android:name=\"android.permission.WRITE_SETTINGS\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_MICROPHONE\" />\n    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n""",
)
replace_once(
    manifest,
    """        <!-- Discover installed launchable apps -->\n""",
    """        <package android:name=\"com.google.android.gm\" />\n        <package android:name=\"com.whatsapp\" />\n\n        <!-- Discover installed launchable apps -->\n""",
)
replace_once(
    manifest,
    """        <activity\n            android:name=\".CloudConfigActivity\"\n""",
    """        <service\n            android:name=\"com.jarvis.core.device.JarvisAppAutomationService\"\n            android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\"\n            android:exported=\"true\"\n            android:label=\"@string/app_automation_service_label\">\n            <intent-filter>\n                <action android:name=\"android.accessibilityservice.AccessibilityService\" />\n            </intent-filter>\n            <meta-data\n                android:name=\"android.accessibilityservice\"\n                android:resource=\"@xml/jarvis_app_automation_service\" />\n        </service>\n        <service\n            android:name=\".JarvisWakeService\"\n            android:exported=\"false\"\n            android:foregroundServiceType=\"microphone\"\n            android:stopWithTask=\"false\" />\n        <activity\n            android:name=\".CloudConfigActivity\"\n""",
)

replace_once(gradle, "versionCode = 20", "versionCode = 21")
replace_once(
    gradle,
    'versionName = "0.9.10-agent-control-core"',
    'versionName = "0.9.11-local-device-agent"',
)

replace_once(
    main_activity,
    """    private fun openCloudSetupIfRequired() {\n        if (!brain.isCloudConfigured() && !setupOpenedThisSession && !isFinishing) {\n            setupOpenedThisSession = true\n            hud.pushEvent(\"CORTEX MESH -> OPENING SECURE REGISTRY\")\n            startActivity(Intent(this, CloudConfigActivity::class.java))\n        }\n    }\n""",
    """    private fun openCloudSetupIfRequired() {\n        if (!brain.isCloudConfigured()) {\n            hud.pushEvent(\"LOCAL AGENT -> READY // CLOUD CORTEX OPTIONAL\")\n        }\n    }\n""",
)

# Remove the one-shot patch machinery from the resulting branch.
(root / ".github/workflows/apply-local-agent-wire.yml").unlink(missing_ok=True)
Path(__file__).unlink(missing_ok=True)
print("Local device agent wiring applied successfully.")
