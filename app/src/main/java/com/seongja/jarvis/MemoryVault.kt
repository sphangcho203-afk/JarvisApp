package com.seongja.jarvis

import android.content.Context
import java.util.Locale

/**
 * High-level memory API used by Jarvis.
 *
 * Stable profile facts, private contacts, and conversation turns are persisted
 * in the encrypted SQLite store. Sensitive contacts are never included in the
 * cloud prompt context unless the user explicitly asks for them locally.
 */
class MemoryVault(context: Context) {
    data class OwnerUpdate(
        val emailStored: Boolean = false,
        val phoneCount: Int = 0,
        val recoverySet: Boolean = false
    ) {
        val changed: Boolean get() = emailStored || phoneCount > 0 || recoverySet
    }

    private val appContext = context.applicationContext
    private val db = JarvisMemoryDatabase(appContext)
    private val legacyPrefs = appContext.getSharedPreferences(
        "jarvis_phase5_memory",
        Context.MODE_PRIVATE
    )

    init {
        migrateLegacyMemoryOnce()
        seedCoreIdentity()
    }

    fun callsign(): String = db.profileValue("callsign") ?: "Seongja"

    fun profile(): String = buildString {
        append("Operator: ${callsign()}")
        db.profileValue("role")?.let { append(" // Role: $it") }
        db.profileValue("mission")?.let { append(" // Mission: $it") }
    }

    fun setIdentity(name: String) {
        val clean = name.trim().ifBlank { "Seongja" }.take(40)
        db.upsertProfile("callsign", clean)
        addFact("Operator identity updated to $clean.", category = "identity")
    }

    fun setEmail(email: String) {
        val clean = email.trim().lowercase(Locale.US).take(240)
        if (!EMAIL_REGEX.matches(clean)) return
        db.upsertProfile("email", clean, sensitive = true)
        db.addContact("email", "Owner email", clean)
    }

    fun addPhone(number: String, label: String = "Owner phone", recovery: Boolean = false) {
        val clean = normalizePhone(number) ?: return
        db.addContact("phone", label, clean, isRecovery = recovery)
        if (recovery) db.setRecoveryContact(clean)
    }

    fun setRecoveryContact(value: String): Boolean = db.setRecoveryContact(
        normalizePhone(value) ?: value.trim().lowercase(Locale.US)
    )

    fun captureOwnerDetails(text: String): OwnerUpdate {
        val lower = text.lowercase(Locale.getDefault())
        val ownerContext = listOf(
            "my email", "my gmail", "owner email", "my contact", "my phone",
            "my number", "contact number", "owner details", "owner information",
            "recovery contact", "lost phone contact"
        ).any(lower::contains)
        if (!ownerContext) return OwnerUpdate()

        val email = EMAIL_REGEX.find(text)?.value
        if (email != null) setEmail(email)

        val phoneCandidates = PHONE_CANDIDATE_REGEX.findAll(text)
            .mapNotNull { normalizePhone(it.value) }
            .distinct()
            .take(6)
            .toList()
        val recoveryRequested = lower.contains("recovery contact") ||
            lower.contains("lost phone contact") ||
            lower.contains("use this if my phone is lost")

        phoneCandidates.forEachIndexed { index, phone ->
            addPhone(
                number = phone,
                label = if (index == 0) "Owner phone" else "Owner phone ${index + 1}",
                recovery = recoveryRequested && index == 0
            )
        }

        return OwnerUpdate(
            emailStored = email != null,
            phoneCount = phoneCandidates.size,
            recoverySet = recoveryRequested && phoneCandidates.isNotEmpty()
        )
    }

    fun publicOwnerCard(): String = buildString {
        appendLine("OWNER // ${callsign()}")
        val recovery = db.contacts().firstOrNull { it.isRecovery }
        if (recovery != null) {
            appendLine("RECOVERY ${recovery.kind.uppercase(Locale.US)} // ${recovery.value}")
        } else {
            appendLine("PRIVATE CONTACTS // PROTECTED")
            appendLine("Please keep the device powered on and return it to the owner or a trusted authority.")
        }
    }.trim()

    fun privateOwnerCard(): String = buildString {
        appendLine(profile())
        db.profileValue("email")?.let { appendLine("Email: $it") }
        val contacts = db.contacts()
        if (contacts.isEmpty()) {
            appendLine("Contacts: none stored")
        } else {
            appendLine("Contacts:")
            contacts.forEach { contact ->
                appendLine("- ${contact.label}: ${contact.value}${if (contact.isRecovery) " [recovery]" else ""}")
            }
        }
    }.trim()

    fun addFact(
        fact: String,
        category: String = "general",
        sensitive: Boolean = false
    ) {
        db.addFact(fact, category, sensitive)
    }

    fun addHistory(entry: String) {
        val clean = entry.trim()
        if (clean.isBlank()) return
        val role = when {
            clean.startsWith("USER:", ignoreCase = true) -> "user"
            clean.startsWith("JARVIS", ignoreCase = true) -> "assistant"
            else -> "system"
        }
        db.addConversation(role, clean)
    }

    fun addConversation(role: String, content: String) {
        db.addConversation(role, content)
    }

    /**
     * Lightweight local learning. Jarvis stores every turn, but only promotes
     * clearly stable owner statements into long-term facts.
     */
    fun learnFromUserTurn(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        captureOwnerDetails(clean)

        val lower = clean.lowercase(Locale.getDefault())
        if (lower.endsWith("?") || QUESTION_PREFIXES.any(lower::startsWith)) return

        val stableFact = when {
            lower.startsWith("remember that ") -> clean.substringAfter("remember that", "").trim()
            lower.startsWith("i prefer ") -> clean
            lower.startsWith("i like ") -> clean
            lower.startsWith("i dislike ") -> clean
            lower.startsWith("i am building ") -> clean
            lower.startsWith("i'm building ") -> clean
            lower.startsWith("my project ") -> clean
            lower.startsWith("i live in ") -> clean
            lower.startsWith("my goal is ") -> clean
            else -> ""
        }
        if (stableFact.length in 4..500) {
            addFact(stableFact, category = "learned")
        }
    }

    fun clearUserFactsKeepIdentity() {
        db.clearUserMemoryKeepIdentity()
        addFact(
            "Memory cleared. Core identity retained: ${callsign()}.",
            category = "system"
        )
    }

    fun summary(): String {
        val (factCount, contactCount, conversationCount) = db.counts()
        return "OPERATOR ${callsign()} // FACTS $factCount // CONTACTS $contactCount // TURNS $conversationCount"
    }

    fun promptContext(query: String = ""): String = buildString {
        appendLine(profile())
        appendLine("Stored non-sensitive facts:")
        facts().take(18).forEach { appendLine("- ${safeForCloud(it)}") }

        val relevant = if (query.isBlank()) {
            db.recentConversations(10)
        } else {
            db.relevantConversations(query, 10)
        }
        appendLine("Relevant previous conversation:")
        relevant.reversed().forEach { turn ->
            appendLine("- ${turn.role.uppercase(Locale.US)}: ${safeForCloud(turn.content).take(700)}")
        }
        appendLine("Privacy rule: private contacts and raw credentials are not included in cloud context.")
    }.trim()

    fun expanded(includeSensitive: Boolean = false): String = buildString {
        appendLine(profile())
        appendLine(summary())
        appendLine()
        appendLine("Stored facts:")
        facts(includeSensitive).take(60).forEach { appendLine("- $it") }
        appendLine()
        appendLine("Recent conversation:")
        db.recentConversations(30).reversed().forEach { turn ->
            appendLine("${turn.role.uppercase(Locale.US)} // ${turn.content}")
        }
        if (includeSensitive) {
            appendLine()
            appendLine("Private owner data:")
            appendLine(privateOwnerCard())
        }
    }.trim()

    fun facts(includeSensitive: Boolean = false): List<String> =
        db.facts(includeSensitive = includeSensitive)

    fun history(): List<String> = db.recentConversations(100).map { turn ->
        "${turn.role.uppercase(Locale.US)} // ${turn.content}"
    }

    fun hasPrivateOwnerData(): Boolean =
        db.profileValue("email") != null || db.contacts().isNotEmpty()

    private fun migrateLegacyMemoryOnce() {
        if (legacyPrefs.getBoolean(KEY_DB_MIGRATED, false)) return

        legacyPrefs.getString(KEY_CALLSIGN, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { db.upsertProfile("callsign", it) }
        legacyPrefs.getString(KEY_PROFILE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { db.addFact(it, category = "legacy_profile") }
        legacyPrefs.getString(KEY_FACTS, "")
            .orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .forEach { db.addFact(it, category = "legacy") }
        legacyPrefs.getString(KEY_HISTORY, "")
            .orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .forEach { db.addConversation("legacy", it) }

        legacyPrefs.edit().putBoolean(KEY_DB_MIGRATED, true).apply()
    }

    private fun seedCoreIdentity() {
        if (db.profileValue("callsign").isNullOrBlank()) {
            db.upsertProfile("callsign", "Seongja")
        }
        if (db.profileValue("role").isNullOrBlank()) {
            db.upsertProfile("role", "Jarvis creator and operator")
        }
        if (db.profileValue("mission").isNullOrBlank()) {
            db.upsertProfile(
                "mission",
                "Build a phone-first, cloud-connected personal intelligence assistant"
            )
        }
        addFact(
            "Jarvis uses a calm, precise, capable dialogue style and addresses the operator as Sir when appropriate.",
            category = "preference"
        )
        addFact(
            "Jarvis is designed as an advanced geometric Android intelligence system with voice, memory, research, and verified device actions.",
            category = "project"
        )
    }

    private fun safeForCloud(value: String): String = value
        .replace(EMAIL_REGEX, "[private email]")
        .replace(PHONE_CANDIDATE_REGEX, "[private phone]")

    private fun normalizePhone(value: String): String? {
        val hasPlus = value.trim().startsWith("+")
        var digits = value.filter(Char::isDigit)
        if (digits.length == 12 && digits.startsWith("91")) {
            return "+$digits"
        }
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length != 10 || digits.firstOrNull() !in '6'..'9') return null
        return if (hasPlus) "+$digits" else digits
    }

    companion object {
        private const val KEY_DB_MIGRATED = "memory_db_migrated_v1"
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_PROFILE = "profile"
        private const val KEY_FACTS = "facts"
        private const val KEY_HISTORY = "history"

        private val EMAIL_REGEX = Regex(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            RegexOption.IGNORE_CASE
        )
        private val PHONE_CANDIDATE_REGEX = Regex(
            "(?<!\\d)(?:\\+?91[ -]?)?[6-9](?:[ -]?\\d){9}(?!\\d)"
        )
        private val QUESTION_PREFIXES = listOf(
            "what ", "why ", "how ", "when ", "where ", "who ", "which ",
            "can ", "could ", "would ", "will ", "do ", "does ", "did ",
            "is ", "are ", "should ", "tell me ", "explain "
        )
    }
}
