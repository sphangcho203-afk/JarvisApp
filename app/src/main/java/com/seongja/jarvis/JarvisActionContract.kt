package com.seongja.jarvis

import org.json.JSONObject
import java.util.UUID

/**
 * Typed action envelope shared by the cognitive layer and Android executor.
 * Arbitrary shell text and secrets are intentionally excluded.
 */
data class JarvisActionRequest(
    val type: ActionType,
    val target: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false,
    val requestId: String = UUID.randomUUID().toString()
) {
    fun toBrainAction(): BrainAction = BrainAction(
        type = type,
        payload = target,
        label = type.name.lowercase(),
        requestId = requestId,
        requiresConfirmation = requiresConfirmation
    )

    fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        put("target", target)
        put("requires_confirmation", requiresConfirmation)
        put("request_id", requestId)
        put("parameters", JSONObject(parameters))
    }.toString()

    companion object {
        fun secureUnlockRequest(): JarvisActionRequest = JarvisActionRequest(
            type = ActionType.REQUEST_DEVICE_UNLOCK,
            target = "android_keyguard",
            requiresConfirmation = true
        )
    }
}

data class JarvisActionReceipt(
    val requestId: String,
    val actionType: ActionType,
    val accepted: Boolean,
    val confirmed: Boolean,
    val message: String
) {
    fun verifiedSuccess(): Boolean = accepted && confirmed
}
