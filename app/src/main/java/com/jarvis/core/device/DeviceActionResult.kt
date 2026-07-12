package com.jarvis.core.device

/**
 * Verified result returned by the Android execution layer.
 *
 * Cloud reasoning never creates this object. Only deterministic Android code
 * can report an action as successful, blocked, or confirmation-required.
 */
data class DeviceActionResult(
    val actionId: String,
    val target: String,
    val status: DeviceActionStatus,
    val spoken: String,
    val latencyMs: Long,
    val trace: List<String>
)

enum class DeviceActionStatus {
    SUCCESS,
    USER_CONFIRMATION_REQUIRED,
    PERMISSION_REQUIRED,
    FAILED
}
