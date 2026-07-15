package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deterministic Android execution kernel.
 *
 * Local commands execute before cloud reasoning. Every handled command also
 * produces a typed result so the HUD can distinguish success, permission,
 * confirmation, and failure instead of trusting free-form model language.
 */
class DeviceCommandRouter(
    context: Context,
    private val onDeferredResult: (DeviceActionResult) -> Unit = {}
) {

    private val appContext = context.applicationContext
    private val appLauncher = AppLauncher(appContext)
    private val webNavigator = WebNavigator(appContext)
    private val telemetry = DeviceTelemetry(appContext)
    private val localAppAgent = LocalAppAgent(appContext, onDeferredResult)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    @Volatile
    private var torchEnabledByJarvis = false

    fun execute(command: String): String? = executeDetailed(command)?.spoken

    fun executeDetailed(command: String): DeviceActionResult? {
        val started = SystemClock.elapsedRealtime()
        val raw = command.trim()
        if (raw.isBlank()) return null

        localAppAgent.handle(raw, started)?.let { return it }

        val normalized = normalizeNaturalCommand(raw)
        if (normalized.isBlank()) return null
        systemControlCommand(normalized, started)?.let { return it }

        val spoken = executeNormalized(normalized) ?: return null
        val status = resultStatus(spoken)
        val actionId = inferActionId(normalized)
        return DeviceActionResult(
            actionId = actionId,
            target = inferTarget(normalized, actionId),
            status = status,
            spoken = spoken,
            latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
            trace = listOf(
                "android_action_fabric",
                "natural_command_normalized",
                "action=$actionId",
                "status=${status.name.lowercase(Locale.US)}"
            )
        )
    }

    private fun executeNormalized(command: String): String? {
        capabilityCommand(command)?.let { return it }
        timeCommand(command)?.let { return it }
        dateCommand(command)?.let { return it }
        batteryCommand(command)?.let { return it }
        networkStatusCommand(command)?.let { return it }
        flashlightCommand(command)?.let { return it }
        volumeCommand(command)?.let { return it }
        mediaCommand(command)?.let { return it }
        brightnessCommand(command)?.let { return it }
        rotationCommand(command)?.let { return it }
        spotifyCommand(command)?.let { return it }
        youtubeCommand(command)?.let { return it }
        settingsCommand(command)?.let { return it }

        return when {
            isExplicitWebCommand(command) -> webNavigator.describe(webNavigator.browse(command))
            isOpenCommand(command) -> {
                val target = removeOpenPrefix(command)
                if (looksLikeWebsite(target)) {
                    webNavigator.describe(webNavigator.openWebsite(target))
                } else {
                    appLauncher.describe(appLauncher.openApp(target))
                }
            }
            else -> tryBareAppName(command)
        }
    }

    private fun capabilityCommand(command: String): String? {
        val matches = command == "capability status" ||
            command == "device controls" ||
            command == "what can you control" ||
            command == "what can you do on my phone"
        if (!matches) return null
        return "Android action fabric online. I can launch apps, browse, report device telemetry, control media and settings, compose Gmail drafts, send confirmed Gmail or WhatsApp messages, read the current screen on demand, and use the optional local wake listener."
    }

    private fun timeCommand(command: String): String? {
        val matches = command in setOf(
            "time",
            "current time",
            "tell me the time",
            "what time is it",
            "what is the time",
            "whats the time",
            "give me the time"
        ) || command.contains("current time")
        if (!matches) return null
        val formatted = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        return "It is $formatted, Sir."
    }

    private fun dateCommand(command: String): String? {
        val matches = command in setOf(
            "date",
            "current date",
            "todays date",
            "what date is it",
            "what is todays date",
            "what day is it",
            "tell me the date"
        )
        if (!matches) return null
        val formatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
        return "Today is $formatted, Sir."
    }

    private fun batteryCommand(command: String): String? {
        val matches = command == "battery" ||
            command == "battery status" ||
            command == "battery level" ||
            command.contains("how much battery") ||
            command.contains("battery percentage") ||
            command.contains("what is my battery") ||
            command.contains("check my battery")
        return if (matches) telemetry.battery().spoken() else null
    }

    private fun networkStatusCommand(command: String): String? {
        val matches = command == "network status" ||
            command == "internet status" ||
            command == "am i online" ||
            command == "are we online" ||
            command.contains("check internet connection") ||
            command.contains("check my internet")
        return if (matches) telemetry.network().spoken() else null
    }

    private fun flashlightCommand(command: String): String? {
        val mentionsFlashlight = command.contains("flashlight") || command.contains("torch")
        if (!mentionsFlashlight) return null

        return when {
            containsOff(command) -> setTorch(false)
            containsOn(command) -> setTorch(true)
            command.startsWith("toggle ") || command == "flashlight" || command == "torch" -> setTorch(!torchEnabledByJarvis)
            else -> null
        }
    }

    private fun setTorch(enabled: Boolean): String {
        val cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
            ?: return "This phone did not expose a controllable flashlight to Jarvis."

        return runCatching {
            cameraManager.setTorchMode(cameraId, enabled)
            torchEnabledByJarvis = enabled
            if (enabled) "Flashlight on." else "Flashlight off."
        }.getOrElse { error ->
            "I couldn't change the flashlight because the camera is busy or unavailable: ${error.message ?: error.javaClass.simpleName}."
        }
    }

    private fun volumeCommand(command: String): String? {
        val exactPercent = Regex("(?:set )?(?:media )?volume(?: to| at)? (\\d{1,3})(?: percent|%)?")
            .find(command)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        if (exactPercent != null) {
            val percent = exactPercent.coerceIn(0, 100)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val desired = ((percent / 100f) * max).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, desired, AudioManager.FLAG_SHOW_UI)
            return "Media volume set to $percent percent."
        }

        return when {
            command in setOf("mute", "mute volume", "mute audio", "mute media") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Media audio muted."
            }
            command in setOf("unmute", "unmute volume", "unmute audio", "unmute media") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                "Media audio unmuted."
            }
            command.contains("volume up") || command.contains("increase volume") || command == "louder" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Media volume increased."
            }
            command.contains("volume down") || command.contains("decrease volume") || command == "quieter" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Media volume decreased."
            }
            else -> null
        }
    }

    private fun mediaCommand(command: String): String? {
        val keyCode = when (command) {
            "play music", "resume music", "resume playback", "play media" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "pause music", "pause playback", "pause media" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play pause", "toggle playback" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "next song", "next track", "skip song", "skip track" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "previous song", "previous track", "last song", "last track" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop music", "stop playback", "stop media" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> null
        } ?: return null

        dispatchMediaKey(keyCode)
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "Play command sent to the active media app."
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "Playback paused."
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Skipping to the next track."
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Returning to the previous track."
            KeyEvent.KEYCODE_MEDIA_STOP -> "Playback stopped."
            else -> "Playback toggled."
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    private fun brightnessCommand(command: String): String? {
        if (!command.contains("brightness")) return null

        if (!Settings.System.canWrite(appContext)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            )
            openIntent(intent)
            return "Android needs one-time permission before Jarvis can change brightness. The permission screen is open."
        }

        val current = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        )
        val requestedPercent = Regex("brightness(?: to| at)? (\\d{1,3})(?: percent|%)?")
            .find(command)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val target = when {
            requestedPercent != null -> ((requestedPercent.coerceIn(1, 100) / 100f) * 255f).toInt()
            command.contains("brightness up") || command.contains("increase brightness") || command.contains("brighter") -> current + 32
            command.contains("brightness down") || command.contains("decrease brightness") || command.contains("dimmer") -> current - 32
            else -> return null
        }.coerceIn(1, 255)

        Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
        val percent = ((target / 255f) * 100f).toInt()
        return "Screen brightness set to about $percent percent."
    }

    private fun rotationCommand(command: String): String? {
        val mentionsRotation = command.contains("auto rotate") || command.contains("screen rotation")
        if (!mentionsRotation) return null

        if (!Settings.System.canWrite(appContext)) {
            openIntent(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${appContext.packageName}")
                )
            )
            return "Android needs one-time permission before Jarvis can change screen rotation. The permission screen is open."
        }

        val enabled = when {
            containsOff(command) -> false
            containsOn(command) -> true
            else -> return null
        }
        Settings.System.putInt(
            appContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (enabled) 1 else 0
        )
        return if (enabled) "Auto-rotate enabled." else "Auto-rotate disabled."
    }

    private fun spotifyCommand(command: String): String? {
        if (!command.contains("spotify")) return null
        if (isOpenCommand(command) || command == "spotify") {
            return appLauncher.describe(appLauncher.openApp("spotify"))
        }

        val query = extractServiceQuery(command, "spotify") ?: return null
        val encoded = Uri.encode(query)
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            appContext.startActivity(appIntent)
            true
        }.getOrDefault(false)

        if (!opened) {
            openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")))
        }
        return "Opening Spotify search for $query."
    }

    private fun youtubeCommand(command: String): String? {
        if (!command.contains("youtube")) return null
        if (isOpenCommand(command) || command == "youtube") {
            return appLauncher.describe(appLauncher.openApp("youtube"))
        }

        val query = extractServiceQuery(command, "youtube") ?: return null
        val target = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        return "Opening YouTube results for $query."
    }

    private fun extractServiceQuery(raw: String, service: String): String? {
        val patterns = listOf(
            Regex("(?i)^(?:play|search(?: for)?|find|look up)\\s+(.+?)\\s+(?:on|in)\\s+$service[.!?]*$"),
            Regex("(?i)^$service\\s+(?:search(?: for)?|find|play)?\\s*(.+?)[.!?]*$")
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(raw.trim())?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun systemControlCommand(
        command: String,
        started: Long
    ): DeviceActionResult? {
        if (command == "system control status" || command == "controller status") {
            val enabled = SystemControlAccess.isEnabled(appContext)
            val connected = JarvisSystemControlService.isConnected()
            val message = when {
                enabled && connected -> "System Control is online. Quick Settings execution is ready."
                enabled -> "System Control is enabled and reconnecting."
                else -> "System Control is disabled. Say enable system control to open the one-time setup."
            }
            return DeviceActionResult(
                actionId = "system_control_status",
                target = "system control",
                status = if (enabled) DeviceActionStatus.SUCCESS else DeviceActionStatus.PERMISSION_REQUIRED,
                spoken = message,
                latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                trace = listOf("android_system_control_bridge", "status_probe")
            )
        }

        if (command == "enable system control" || command == "open system control" || command == "system control setup") {
            val opened = SystemControlAccess.openSettings(appContext)
            return DeviceActionResult(
                actionId = "system_control_setup",
                target = "accessibility settings",
                status = if (opened) DeviceActionStatus.PERMISSION_REQUIRED else DeviceActionStatus.FAILED,
                spoken = if (opened) {
                    "Enable Jarvis System Control once. It is restricted to Android Quick Settings."
                } else {
                    "I couldn't open the System Control setup."
                },
                latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                trace = listOf("android_system_control_bridge", "open_accessibility_settings")
            )
        }

        if (command.contains("internet") &&
            !command.contains("wifi") &&
            !command.contains("wi fi") &&
            !command.contains("mobile data") &&
            !command.contains("cellular data") &&
            (containsOn(command) || containsOff(command))
        ) {
            return DeviceActionResult(
                actionId = "internet_target",
                target = "internet",
                status = DeviceActionStatus.NEEDS_CLARIFICATION,
                spoken = "Specify Wi-Fi or mobile data, Sir.",
                latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                trace = listOf("android_system_control_bridge", "ambiguous_internet_target")
            )
        }

        val toggle = SystemToggle.fromCommand(command) ?: return null
        val desired = when {
            containsOff(command) -> false
            containsOn(command) -> true
            else -> return null
        }

        if (!SystemControlAccess.isEnabled(appContext)) {
            val opened = SystemControlAccess.openSettings(appContext)
            return DeviceActionResult(
                actionId = toggle.id,
                target = toggle.displayName,
                status = if (opened) DeviceActionStatus.PERMISSION_REQUIRED else DeviceActionStatus.FAILED,
                spoken = if (opened) {
                    "Enable Jarvis System Control once, then repeat the command."
                } else {
                    "System Control is unavailable."
                },
                latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                trace = listOf(
                    "android_system_control_bridge",
                    "target=${toggle.id}",
                    "accessibility_permission_required"
                )
            )
        }

        if (!JarvisSystemControlService.isConnected()) {
            return DeviceActionResult(
                actionId = toggle.id,
                target = toggle.displayName,
                status = DeviceActionStatus.FAILED,
                spoken = "System Control is enabled but not connected yet. Reopen Jarvis and try again.",
                latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                trace = listOf(
                    "android_system_control_bridge",
                    "target=${toggle.id}",
                    "service_not_connected"
                )
            )
        }

        val accepted = JarvisSystemControlService.request(
            SystemControlRequest(toggle, desired)
        ) { outcome ->
            onDeferredResult(outcome.toDeviceActionResult())
        }

        return DeviceActionResult(
            actionId = toggle.id,
            target = toggle.displayName,
            status = if (accepted) DeviceActionStatus.IN_PROGRESS else DeviceActionStatus.FAILED,
            spoken = if (accepted) {
                "Executing ${toggle.displayName} ${if (desired) "on" else "off"}."
            } else {
                "System Control is busy. Try again in a moment."
            },
            latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
            trace = listOf(
                "android_system_control_bridge",
                "systemui_quick_settings",
                "target=${toggle.id}",
                "desired=${if (desired) "on" else "off"}",
                if (accepted) "queued" else "queue_rejected"
            )
        )
    }

    private fun settingsCommand(command: String): String? {
        return when {
            command.contains("internet") && (containsOn(command) || containsOff(command) || command.contains("settings")) -> {
                openInternetPanel()
                "Internet controls opened. Android requires one tap to change Wi-Fi or mobile data."
            }
            command.contains("wifi") && (containsOn(command) || containsOff(command) || command.contains("settings")) -> {
                openWifiPanel()
                "Wi-Fi controls opened. Android requires one tap to change the radio."
            }
            command.contains("mobile data") || command.contains("cellular data") -> {
                openInternetPanel()
                "Mobile-data controls opened. Android requires one tap to change the radio."
            }
            command.contains("bluetooth") && (containsOn(command) || containsOff(command) || command.contains("settings")) -> {
                openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "Bluetooth controls opened. Android requires confirmation to change the radio."
            }
            (command.contains("location") || command.contains("gps")) && (containsOn(command) || containsOff(command) || command.contains("settings")) -> {
                openIntent(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                "Location controls opened. Android requires confirmation to change location access."
            }
            command.contains("airplane mode") -> {
                openIntent(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                "Airplane-mode controls opened."
            }
            command.contains("hotspot") || command.contains("tethering") -> {
                openIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                "Hotspot and tethering controls opened."
            }
            command.contains("nfc") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    openIntent(Intent(Settings.Panel.ACTION_NFC))
                } else {
                    openIntent(Intent(Settings.ACTION_NFC_SETTINGS))
                }
                "NFC controls opened."
            }
            command.contains("do not disturb") || command == "dnd settings" -> {
                openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                "Do Not Disturb controls opened."
            }
            command.contains("battery saver") -> {
                openIntent(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                "Battery-saver controls opened."
            }
            command == "open settings" || command == "settings" -> {
                openIntent(Intent(Settings.ACTION_SETTINGS))
                "Opening Android settings."
            }
            command == "open quick settings" || command == "quick settings" -> {
                openIntent(Intent(Settings.ACTION_SETTINGS))
                "Opening Android settings."
            }
            else -> null
        }
    }

    private fun openInternetPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } else {
            openIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun openWifiPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openIntent(Intent(Settings.Panel.ACTION_WIFI))
        } else {
            openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun openIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun tryBareAppName(command: String): String? {
        if (command.length > 48 || command.split(" ").size > 4) return null
        if (QUESTION_PREFIXES.any(command::startsWith)) return null
        return when (val result = appLauncher.openApp(command)) {
            is AppLauncher.LaunchResult.NotFound -> null
            else -> appLauncher.describe(result)
        }
    }

    private fun normalizeNaturalCommand(value: String): String {
        var command = normalize(value)
        command = command.replace(Regex("^(?:hey\\s+)?jarvis\\s+"), "")

        var changed: Boolean
        do {
            val before = command
            command = command
                .replace(Regex("^(?:please|kindly)\\s+"), "")
                .replace(Regex("^(?:can|could|would|will)\\s+(?:you|u)\\s+"), "")
                .replace(Regex("^(?:i\\s+want\\s+you\\s+to|i\\s+need\\s+you\\s+to)\\s+"), "")
            changed = command != before
        } while (changed)

        command = command
            .replace(Regex("\\s+(?:for me|right now|now please|please)$"), "")
            .trim()
        return command
    }

    private fun isExplicitWebCommand(command: String): Boolean =
        command.startsWith("browse ") ||
            command.startsWith("browse for ") ||
            command.startsWith("search ") ||
            command.startsWith("search for ") ||
            command.startsWith("look up ") ||
            command.startsWith("google ")

    private fun isOpenCommand(command: String): Boolean = OPEN_PATTERN.matches(command)

    private fun removeOpenPrefix(command: String): String = OPEN_PATTERN
        .find(command)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.replace(Regex("^(?:the)\\s+"), "")
        ?.replace(Regex("\\s+(?:application|app)$"), "")
        .orEmpty()

    private fun inferActionId(command: String): String = when {
        timeCommand(command) != null -> "read_time"
        dateCommand(command) != null -> "read_date"
        command.contains("battery") -> "read_battery"
        command.contains("network") || command.contains("internet status") -> "read_network"
        command.contains("flashlight") || command.contains("torch") -> "flashlight"
        command.contains("brightness") -> "brightness"
        command.contains("auto rotate") || command.contains("screen rotation") -> "rotation"
        command.contains("volume") || command in setOf("mute", "unmute", "louder", "quieter") -> "media_volume"
        command.contains("spotify") -> "spotify"
        command.contains("youtube") -> "youtube"
        command.contains("wifi") || command.contains("mobile data") || command.contains("bluetooth") ||
            command.contains("location") || command.contains("airplane mode") || command.contains("hotspot") ||
            command.contains("nfc") || command.contains("battery saver") || command.contains("do not disturb") ||
            command.contains("flight mode") || command.contains("eye comfort") || command.contains("night light") -> "system_control"
        isExplicitWebCommand(command) -> "web_search"
        isOpenCommand(command) -> if (looksLikeWebsite(removeOpenPrefix(command))) "open_website" else "open_app"
        else -> "open_app"
    }

    private fun inferTarget(command: String, actionId: String): String = when (actionId) {
        "open_app", "open_website" -> removeOpenPrefix(command)
        "spotify" -> "spotify"
        "youtube" -> "youtube"
        "flashlight" -> "flashlight"
        "brightness" -> "screen"
        "rotation" -> "screen"
        "media_volume" -> "media"
        "read_battery" -> "battery"
        "read_network" -> "network"
        "read_time" -> "clock"
        "read_date" -> "calendar"
        else -> command.take(80)
    }

    private fun resultStatus(spoken: String): DeviceActionStatus {
        val lower = spoken.lowercase(Locale.US)
        return when {
            "executing " in lower -> DeviceActionStatus.IN_PROGRESS
            "tile was pressed" in lower -> DeviceActionStatus.EXECUTED_UNVERIFIED
            "one-time permission" in lower || "permission screen" in lower || "enable jarvis system control" in lower -> DeviceActionStatus.PERMISSION_REQUIRED
            "requires one tap" in lower || "requires confirmation" in lower || "controls opened" in lower -> DeviceActionStatus.USER_CONFIRMATION_REQUIRED
            "couldn't" in lower || "did not expose" in lower || "unavailable" in lower || "could not" in lower -> DeviceActionStatus.FAILED
            else -> DeviceActionStatus.SUCCESS
        }
    }

    private fun looksLikeWebsite(value: String): Boolean =
        value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("www.") ||
            Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+([/?#].*)?$").matches(value)

    private fun containsOn(command: String): Boolean =
        Regex("\\b(on|enable|enabled|activate|start)\\b").containsMatchIn(command)

    private fun containsOff(command: String): Boolean =
        Regex("\\b(off|disable|disabled|deactivate|stop)\\b").containsMatchIn(command)

    private fun normalize(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace("what's", "whats")
        .replace(Regex("[^a-z0-9%:/._ -]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private val OPEN_PATTERN = Regex(
            "^(?:open(?: up)?|launch|start|run|bring up|take me to|go to)\\s+(.+)$"
        )
        private val QUESTION_PREFIXES = listOf(
            "what ", "why ", "how ", "when ", "where ", "who ", "tell me ", "explain ", "is ", "are "
        )
    }
}
