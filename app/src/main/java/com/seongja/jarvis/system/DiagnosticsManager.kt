package com.seongja.jarvis.system

import com.seongja.jarvis.models.TelemetryState

class DiagnosticsManager {
    fun compactReport(t: TelemetryState): String {
        val battery = if (t.batteryPercent >= 0) "BAT ${t.batteryPercent}%" else "BAT ?"
        val charge = if (t.charging) "CHG" else "BATTERY"
        val memory = "MEM ${t.memoryUsedMb}/${t.memoryMaxMb}MB"
        val storage = "STORE %.1fGB FREE".format(t.storageFreeGb)
        return "$battery $charge | NET ${t.network} | $memory | $storage | UP ${t.uptimeLabel}"
    }

    fun longReport(t: TelemetryState): String {
        return buildString {
            appendLine("Advanced diagnostics report.")
            appendLine("Battery: ${if (t.batteryPercent >= 0) "${t.batteryPercent}%" else "unknown"} ${if (t.charging) "charging" else "discharging"}.")
            appendLine("Network: ${t.network}.")
            appendLine("Runtime memory: ${t.memoryUsedMb} of ${t.memoryMaxMb} megabytes.")
            appendLine("Free device storage: %.1f gigabytes.".format(t.storageFreeGb))
            appendLine("Uptime: ${t.uptimeLabel}.")
            appendLine("Commands executed: ${t.commandCount}.")
            appendLine("Microphone: ${if (t.microphoneReady) "ready" else "standby"}.")
            append("Permission layer: ${t.permissionStatus}.")
        }
    }
}
