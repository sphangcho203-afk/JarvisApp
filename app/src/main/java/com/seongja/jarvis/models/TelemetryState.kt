package com.seongja.jarvis.models

data class TelemetryState(
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val network: String = "UNKNOWN",
    val memoryUsedMb: Long = 0L,
    val memoryMaxMb: Long = 0L,
    val storageFreeGb: Double = 0.0,
    val uptimeSeconds: Long = 0L,
    val commandCount: Int = 0,
    val microphoneReady: Boolean = false,
    val permissionStatus: String = "PENDING"
) {
    val uptimeLabel: String
        get() {
            val hours = uptimeSeconds / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            val seconds = uptimeSeconds % 60
            return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
        }
}
