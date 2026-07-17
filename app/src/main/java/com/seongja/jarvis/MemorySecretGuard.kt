package com.seongja.jarvis

object MemorySecretGuard {
    private val privateKey = Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", RegexOption.IGNORE_CASE)
    private val bearer = Regex("\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}", RegexOption.IGNORE_CASE)
    private val knownApiKey = Regex("\\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{20,})\\b")
    private val credentialAssignment = Regex(
        "\\b(password|passcode|pin|secret|api[_ -]?key|access[_ -]?token|refresh[_ -]?token|authorization)\\s*[:=]\\s*\\S+",
        RegexOption.IGNORE_CASE
    )
    private val otp = Regex(
        "\\b(otp|one[ -]?time password|verification code|login code|auth code)\\b[^\\d]{0,16}\\d{4,8}\\b",
        RegexOption.IGNORE_CASE
    )

    fun rejectionReason(value: String): String? = when {
        privateKey.containsMatchIn(value) -> "Private keys cannot be stored in conversational memory."
        bearer.containsMatchIn(value) -> "Bearer tokens cannot be stored in conversational memory."
        knownApiKey.containsMatchIn(value) -> "API keys cannot be stored in conversational memory."
        credentialAssignment.containsMatchIn(value) -> "Passwords, PINs, secrets, and tokens cannot be stored in conversational memory."
        otp.containsMatchIn(value) -> "One-time authentication codes cannot be stored in conversational memory."
        else -> null
    }
}

class MemoryPolicyException(message: String) : IllegalArgumentException(message)
