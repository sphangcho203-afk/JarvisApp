package com.jarvis.core.device

import java.util.Locale

enum class SystemToggle(
    val id: String,
    val displayName: String,
    val commandAliases: List<String>,
    val tileLabels: List<String>
) {
    WIFI(
        id = "wifi",
        displayName = "Wi-Fi",
        commandAliases = listOf("wifi", "wi fi", "wireless internet", "wlan"),
        tileLabels = listOf("wifi", "wi fi", "wlan")
    ),
    MOBILE_DATA(
        id = "mobile_data",
        displayName = "Mobile data",
        commandAliases = listOf("mobile data", "cellular data", "data connection", "mobile network"),
        tileLabels = listOf("mobile data", "cellular data", "mobile network")
    ),
    HOTSPOT(
        id = "hotspot",
        displayName = "Hotspot",
        commandAliases = listOf("hotspot", "mobile hotspot", "personal hotspot", "tethering"),
        tileLabels = listOf("hotspot", "mobile hotspot", "personal hotspot")
    ),
    AIRPLANE_MODE(
        id = "airplane_mode",
        displayName = "Flight mode",
        commandAliases = listOf("airplane mode", "flight mode", "aeroplane mode"),
        tileLabels = listOf("airplane mode", "flight mode", "aeroplane mode")
    ),
    BLUETOOTH(
        id = "bluetooth",
        displayName = "Bluetooth",
        commandAliases = listOf("bluetooth"),
        tileLabels = listOf("bluetooth")
    ),
    LOCATION(
        id = "location",
        displayName = "Location",
        commandAliases = listOf("location", "gps"),
        tileLabels = listOf("location", "gps")
    ),
    EYE_COMFORT(
        id = "eye_comfort",
        displayName = "Eye comfort",
        commandAliases = listOf(
            "eye comfort",
            "eye protection",
            "night light",
            "blue light filter",
            "vision comfort",
            "eye comfort mode",
            "night shield"
        ),
        tileLabels = listOf(
            "eye comfort",
            "eye protection",
            "night light",
            "blue light filter",
            "vision comfort",
            "eye comfort mode",
            "night shield"
        )
    ),
    DARK_MODE(
        id = "dark_mode",
        displayName = "Dark mode",
        commandAliases = listOf("dark mode", "dark theme"),
        tileLabels = listOf("dark mode", "dark theme")
    ),
    EXTRA_DIM(
        id = "extra_dim",
        displayName = "Extra dim",
        commandAliases = listOf("extra dim", "dim mode"),
        tileLabels = listOf("extra dim", "dim mode")
    ),
    DO_NOT_DISTURB(
        id = "do_not_disturb",
        displayName = "Do Not Disturb",
        commandAliases = listOf("do not disturb", "dnd", "do not disturb mode"),
        tileLabels = listOf("do not disturb", "dnd", "do not disturb mode")
    ),
    BATTERY_SAVER(
        id = "battery_saver",
        displayName = "Battery saver",
        commandAliases = listOf("battery saver", "power saving mode", "power saver", "power saving"),
        tileLabels = listOf("battery saver", "power saving mode", "power saver", "power saving")
    ),
    NFC(
        id = "nfc",
        displayName = "NFC",
        commandAliases = listOf("nfc"),
        tileLabels = listOf("nfc")
    );

    companion object {
        fun fromCommand(command: String): SystemToggle? {
            val normalized = normalize(command)
            return entries.firstOrNull { toggle ->
                toggle.commandAliases.any { alias -> normalized.contains(normalize(alias)) }
            }
        }

        private fun normalize(value: String): String = value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

data class SystemControlRequest(
    val toggle: SystemToggle,
    val desiredEnabled: Boolean
)

data class SystemControlOutcome(
    val request: SystemControlRequest,
    val status: DeviceActionStatus,
    val message: String,
    val latencyMs: Long,
    val trace: List<String>
) {
    fun toDeviceActionResult(): DeviceActionResult = DeviceActionResult(
        actionId = request.toggle.id,
        target = request.toggle.displayName,
        status = status,
        spoken = message,
        latencyMs = latencyMs,
        trace = trace
    )
}
