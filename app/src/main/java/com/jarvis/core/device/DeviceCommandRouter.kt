package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import java.util.Locale

/**
 * Deterministic Android execution kernel.
 *
 * Device actions are handled here before any cloud request. Direct actions are
 * executed only through public Android APIs. Controls that Android reserves for
 * the user or privileged system apps open the smallest official system panel.
 */
class DeviceCommandRouter(context: Context) {

    private val appContext = context.applicationContext
    private val appLauncher = AppLauncher(appContext)
    private val webNavigator = WebNavigator(appContext)
    private val telemetry = DeviceTelemetry(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    @Volatile
    private var torchEnabledByJarvis = false

    fun execute(command: String): String? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null

        capabilityCommand(normalized)?.let { return it }
        batteryCommand(normalized)?.let { return it }
        networkStatusCommand(normalized)?.let { return it }
        flashlightCommand(normalized)?.let { return it }
        volumeCommand(normalized)?.let { return it }
        mediaCommand(normalized)?.let { return it }
        brightnessCommand(normalized)?.let { return it }
        rotationCommand(normalized)?.let { return it }
        spotifyCommand(command, normalized)?.let { return it }
        youtubeCommand(command, normalized)?.let { return it }
        settingsCommand(normalized)?.let { return it }

        return when {
            isExplicitWebCommand(normalized) -> {
                webNavigator.describe(webNavigator.browse(normalized))
            }

            isOpenCommand(normalized) -> {
                val target = removeOpenPrefix(normalized)
                if (looksLikeWebsite(target)) {
                    webNavigator.describe(webNavigator.openWebsite(target))
                } else {
                    appLauncher.describe(appLauncher.openApp(target))
                }
            }

            else -> tryBareAppName(normalized)
        }
    }

    private fun capabilityCommand(command: String): String? {
        val matches = command == "capability status" ||
            command == "device controls" ||
            command == "what can you control" ||
            command == "what can you do on my phone"
        if (!matches) return null
        return "Android execution kernel online. I can control the flashlight, media playback, media volume, brightness and rotation after permission, read live battery and network state, open apps and web searches, search Spotify and YouTube, run timers, and open protected Android control panels."
    }

    private fun batteryCommand(command: String): String? {
        val matches = command == "battery" ||
            command == "battery status" ||
            command == "battery level" ||
            command.contains("how much battery") ||
            command.contains("battery percentage") ||
            command.contains("what is my battery")
        return if (matches) telemetry.battery().spoken() else null
    }

    private fun networkStatusCommand(command: String): String? {
        val matches = command == "network status" ||
            command == "internet status" ||
            command == "am i online" ||
            command == "are we online" ||
            command.contains("check internet connection")
        return if (matches) telemetry.network().spoken() else null
    }

    private fun flashlightCommand(command: String): String? {
        val mentionsFlashlight = command.contains("flashlight") || command.contains("torch")
        if (!mentionsFlashlight) return null

        return when {
            containsOff(command) -> setTorch(false)
            containsOn(command) -> setTorch(true)
            command.startsWith("toggle ") || command == "flashlight" || command == "torch" -> {
                setTorch(!torchEnabledByJarvis)
            }
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

    private fun spotifyCommand(raw: String, command: String): String? {
        if (!command.contains("spotify")) return null
        if (isOpenCommand(command) || command == "spotify") {
            return appLauncher.describe(appLauncher.openApp("spotify"))
        }

        val query = extractServiceQuery(raw, "spotify") ?: return null
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
            openIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/$encoded")
                )
            )
        }
        return "Opening Spotify search for $query."
    }

    private fun youtubeCommand(raw: String, command: String): String? {
        if (!command.contains("youtube")) return null
        if (isOpenCommand(command) || command == "youtube") {
            return appLauncher.describe(appLauncher.openApp("youtube"))
        }

        val query = extractServiceQuery(raw, "youtube") ?: return null
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
        return when (val result = appLauncher.openApp(command)) {
            is AppLauncher.LaunchResult.NotFound -> null
            else -> appLauncher.describe(result)
        }
    }

    private fun isExplicitWebCommand(command: String): Boolean =
        command.startsWith("browse ") ||
            command.startsWith("browse for ") ||
            command.startsWith("search ") ||
            command.startsWith("search for ") ||
            command.startsWith("look up ")

    private fun isOpenCommand(command: String): Boolean =
        command.startsWith("open ") ||
            command.startsWith("launch ") ||
            command.startsWith("start ")

    private fun removeOpenPrefix(command: String): String = command
        .removePrefix("open ")
        .removePrefix("launch ")
        .removePrefix("start ")
        .trim()

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
        .replace(Regex("[^a-z0-9%:/._ -]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
