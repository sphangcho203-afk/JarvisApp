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

    data class MemoryConflict(
        val category: String,
        val previous: String,
        val proposed: String
    )

    private data class StableClaim(
        val category: String,
        val value: String,
        val canonical: String
    )

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

    fun addCorrection(value: String): String {
        val clean = value.trim().trimEnd('.', '?', '!').take(500)
        if (clean.isBlank()) return ""
        val claim = stableClaim(clean)
        val stored = if (claim != null) {
            "CORRECTION[${claim.category.uppercase(Locale.US)}]: ${claim.canonical}"
        } else {
            "CORRECTION: $clean"
        }
        addFact(stored, category = "correction")
        return stored
    }

    fun detectStableConflict(text: String): MemoryConflict? {
        val proposed = stableClaim(text) ?: return null
        val previous = facts()
            .asSequence()
            .mapNotNull(::stableClaimFromStoredFact)
            .firstOrNull { it.category == proposed.category }
            ?: return null

        if (normalizeFact(previous.canonical) == normalizeFact(proposed.canonical)) return null
        return MemoryConflict(
            category = proposed.category,
            previous = previous.canonical,
            proposed = proposed.canonical
        )
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
     * clearly stable owner statements into long-term facts. A conflicting
     * single-valued claim is not silently accepted; Jarvis asks for correction.
     */
    fun learnFromUserTurn(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        captureOwnerDetails(clean)

        val lower = clean.lowercase(Locale.getDefault())
        if (lower.endsWith("?") || QUESTION_PREFIXES.any(lower::startsWith)) return

        if (lower.startsWith("remember that ")) {
            val explicit = clean.substringAfter("remember that", "").trim()
            if (explicit.length in 4..500) addFact(explicit, category = "explicit")
            return
        }

        val stable = stableClaim(clean)
        if (stable != null) {
            if (detectStableConflict(clean) == null) {
                addFact(stable.canonical, category = stable.category)
            }
            return
        }

        val generalFact = when {
            lower.startsWith("i like ") -> clean
            lower.startsWith("i dislike ") -> clean
            lower.startsWith("i am building ") -> clean
            lower.startsWith("i'm building ") -> clean
            lower.startsWith("my project ") -> clean
            else -> ""
        }
        if (generalFact.length in 4..500) {
            addFact(generalFact, category = "learned")
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
        appendLine("OWNER PROFILE")
        appendLine(profile())
        appendLine("Identity core: ${OwnerIdentityCore.VERSION}")
        appendLine()

        val storedFacts = facts()
        val corrections = storedFacts.filter { it.startsWith("CORRECTION", ignoreCase = true) }
        if (corrections.isNotEmpty()) {
            appendLine("CONFIRMED CORRECTIONS, THESE OVERRIDE OLDER CONFLICTING MEMORIES:")
            corrections.take(12).forEach { appendLine("- ${safeForCloud(it)}") }
            appendLine()
        }

        appendLine("STORED NON-SENSITIVE FACTS:")
        storedFacts
            .filterNot { it.startsWith("CORRECTION", ignoreCase = true) }
            .take(22)
            .forEach { appendLine("- ${safeForCloud(it)}") }

        val relevant = if (query.isBlank()) {
            db.recentConversations(10)
        } else {
            db.relevantConversations(query, 12)
        }
        appendLine()
        appendLine("RELEVANT PREVIOUS CONVERSATION:")
        relevant.reversed().forEach { turn ->
            appendLine("- ${turn.role.uppercase(Locale.US)}: ${safeForCloud(turn.content).take(700)}")
        }
        appendLine()
        appendLine("MEMORY RULES: Use corrections before older facts. Use only relevant memories. Private contacts, raw credentials, and sensitive owner data are excluded from cloud context.")
    }.trim()

    fun expanded(includeSensitive: Boolean = false): String = buildString {
        appendLine(OwnerIdentityCore.statusLine(summary()))
        appendLine(profile())
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

    private fun stableClaim(value: String): StableClaim? {
        val clean = value.trim().trimEnd('.', '?', '!')
        val lower = clean.lowercase(Locale.getDefault())
        val definitions = listOf(
            Triple("location", listOf("i live in ", "my location is "), "I live in "),
            Triple("primary_goal", listOf("my goal is ", "my main goal is "), "My goal is "),
            Triple("preferred_language", listOf("my preferred language is ", "i prefer speaking "), "My preferred language is "),
            Triple("current_project", listOf("my current project is ", "the project i am building is "), "My current project is "),
            Triple("communication_preference", listOf("i prefer responses that are ", "i prefer answers that are "), "I prefer responses that are ")
        )

        definitions.forEach { (category, prefixes, canonicalPrefix) ->
            val matched = prefixes.firstOrNull(lower::startsWith) ?: return@forEach
            val payload = clean.substring(matched.length).trim()
            if (payload.length in 2..420) {
                return StableClaim(
                    category = category,
                    value = payload,
                    canonical = canonicalPrefix + payload
                )
            }
        }
        return null
    }

    private fun stableClaimFromStoredFact(value: String): StableClaim? {
        val clean = value
            .substringAfter(":", value)
            .trim()
        return stableClaim(clean)
    }

    private fun normalizeFact(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

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
                "Build a phone-first, cloud-connected, owner-bound personal intelligence assistant"
            )
        }
        addFact(
            "Jarvis uses a calm, precise, capable dialogue style and addresses the operator as Sir when appropriate.",
            category = "preference"
        )
        addFact(
            "Jarvis is Seongja's private owner-bound Android intelligence system with voice, contextual memory, research, and verified device actions.",
            category = "identity_core"
        )
        addFact(
            "Loyalty means honest judgment, privacy, continuity, and stronger alternatives rather than blind agreement.",
            category = "identity_core"
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
        val validIndianMobile = digits.length == 10 &&
            digits.firstOrNull()?.let { it in '6'..'9' } == true
        if (!validIndianMobile) return null
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
