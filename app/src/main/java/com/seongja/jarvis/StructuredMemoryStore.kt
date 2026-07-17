package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class MemoryKind {
    PREFERENCE,
    PROJECT,
    EPISODE,
    IDENTITY,
    CONVERSATION,
    TEMPORARY,
    CORRECTION
}

enum class MemorySource {
    OWNER_DIRECT,
    OWNER_CONFIRMED,
    REPEATED_PATTERN,
    DIARY_APPROVAL,
    CONVERSATION_ARCHIVE,
    LEGACY_IMPORT,
    SYSTEM
}

enum class MemorySensitivity {
    NORMAL,
    PRIVATE,
    HIGHLY_SENSITIVE
}

enum class MemoryRetention {
    SESSION,
    THIRTY_DAYS,
    ONE_YEAR,
    PERMANENT
}

enum class MemoryReviewState {
    PENDING_OWNER_REVIEW,
    CONFIRMED,
    REJECTED
}

data class StructuredMemoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val value: String,
    val kind: MemoryKind = MemoryKind.EPISODE,
    val source: MemorySource = MemorySource.OWNER_DIRECT,
    val confidence: Float = 1f,
    val namespace: String = "general",
    val sensitivity: MemorySensitivity = MemorySensitivity.NORMAL,
    val retention: MemoryRetention = MemoryRetention.PERMANENT,
    val reviewState: MemoryReviewState = MemoryReviewState.CONFIRMED,
    val sourceReference: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs,
    val lastVerifiedAtMs: Long = createdAtMs
) {
    init {
        require(id.isNotBlank()) { "Memory ID cannot be blank." }
        require(value.length <= MAX_VALUE_CHARS) { "Memory value is too long." }
        require(namespace.length <= MAX_NAMESPACE_CHARS) { "Memory namespace is too long." }
        require(confidence in 0f..1f) { "Memory confidence must be between zero and one." }
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = when (retention) {
        MemoryRetention.SESSION -> true
        MemoryRetention.THIRTY_DAYS -> now - updatedAtMs > THIRTY_DAYS_MS
        MemoryRetention.ONE_YEAR -> now - updatedAtMs > ONE_YEAR_MS
        MemoryRetention.PERMANENT -> false
    }

    companion object {
        const val MAX_VALUE_CHARS = 4_000
        const val MAX_NAMESPACE_CHARS = 80
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
        private const val ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L
    }
}

class StructuredMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun list(
        namespace: String? = null,
        includeRejected: Boolean = false,
        includeExpired: Boolean = false
    ): List<StructuredMemoryRecord> {
        val now = System.currentTimeMillis()
        val normalizedNamespace = namespace?.trim()?.lowercase(Locale.US)
        return readRecords()
            .asSequence()
            .filter { includeRejected || it.reviewState != MemoryReviewState.REJECTED }
            .filter { includeExpired || !it.isExpired(now) }
            .filter { normalizedNamespace.isNullOrBlank() || it.namespace == normalizedNamespace }
            .sortedByDescending(StructuredMemoryRecord::updatedAtMs)
            .toList()
    }

    @Synchronized
    fun pendingReview(): List<StructuredMemoryRecord> = list(includeExpired = false)
        .filter { it.reviewState == MemoryReviewState.PENDING_OWNER_REVIEW }

    @Synchronized
    fun find(id: String): StructuredMemoryRecord? = readRecords().firstOrNull { it.id == id }

    @Synchronized
    fun search(query: String): List<StructuredMemoryRecord> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isBlank()) return list()
        return list().filter { record ->
            record.value.lowercase(Locale.getDefault()).contains(normalized) ||
                record.namespace.contains(normalized) ||
                record.kind.name.lowercase(Locale.US).contains(normalized) ||
                record.source.name.lowercase(Locale.US).contains(normalized)
        }
    }

    @Synchronized
    fun upsert(record: StructuredMemoryRecord): StructuredMemoryRecord {
        val clean = record.copy(
            value = record.value.trim().take(StructuredMemoryRecord.MAX_VALUE_CHARS),
            namespace = normalizeNamespace(record.namespace),
            confidence = record.confidence.coerceIn(0f, 1f),
            sourceReference = record.sourceReference.trim().take(240),
            updatedAtMs = System.currentTimeMillis()
        )
        if (clean.value.isBlank()) return clean

        val records = readRecords().toMutableList()
        val duplicate = records.indexOfFirst {
            fingerprint(it.value, it.namespace) == fingerprint(clean.value, clean.namespace)
        }
        val existing = records.indexOfFirst { it.id == clean.id }
        when {
            existing >= 0 -> records[existing] = clean.copy(createdAtMs = records[existing].createdAtMs)
            duplicate >= 0 -> records[duplicate] = clean.copy(
                id = records[duplicate].id,
                createdAtMs = records[duplicate].createdAtMs
            )
            else -> records += clean
        }
        writeRecords(records)
        return clean
    }

    @Synchronized
    fun confirm(id: String): Boolean = updateReviewState(id, MemoryReviewState.CONFIRMED)

    @Synchronized
    fun reject(id: String): Boolean = updateReviewState(id, MemoryReviewState.REJECTED)

    @Synchronized
    fun delete(id: String): Boolean {
        val records = readRecords().toMutableList()
        val changed = records.removeAll { it.id == id }
        if (changed) writeRecords(records)
        return changed
    }

    @Synchronized
    fun clearExpired(): Int {
        val now = System.currentTimeMillis()
        val records = readRecords()
        val kept = records.filterNot { it.isExpired(now) }
        val removed = records.size - kept.size
        if (removed > 0) writeRecords(kept)
        return removed
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun importLegacy(vault: MemoryVault): Int {
        var added = 0
        vault.facts(includeSensitive = false).forEach { fact ->
            val normalized = fact.trim()
            if (normalized.isBlank()) return@forEach
            val exists = readRecords().any {
                fingerprint(it.value, it.namespace) == fingerprint(normalized, inferNamespace(normalized))
            }
            if (!exists) {
                upsert(
                    StructuredMemoryRecord(
                        value = normalized,
                        kind = inferKind(normalized),
                        source = MemorySource.LEGACY_IMPORT,
                        confidence = .75f,
                        namespace = inferNamespace(normalized),
                        sensitivity = MemorySensitivity.NORMAL,
                        retention = MemoryRetention.PERMANENT,
                        reviewState = MemoryReviewState.CONFIRMED,
                        sourceReference = "legacy_memory_vault"
                    )
                )
                added++
            }
        }
        return added
    }

    @Synchronized
    fun queueDiaryCandidate(entryId: String, proposedFact: String): StructuredMemoryRecord? {
        val clean = proposedFact.trim().take(StructuredMemoryRecord.MAX_VALUE_CHARS)
        if (clean.isBlank()) return null
        return upsert(
            StructuredMemoryRecord(
                value = clean,
                kind = MemoryKind.EPISODE,
                source = MemorySource.DIARY_APPROVAL,
                confidence = 1f,
                namespace = "diary",
                sensitivity = MemorySensitivity.PRIVATE,
                retention = MemoryRetention.PERMANENT,
                reviewState = MemoryReviewState.PENDING_OWNER_REVIEW,
                sourceReference = entryId.take(80)
            )
        )
    }

    @Synchronized
    fun exportEncryptedPayload(): String {
        val root = JSONObject().apply {
            put("format", EXPORT_FORMAT)
            put("createdAtMs", System.currentTimeMillis())
            put("records", recordsToJson(readRecords()))
        }
        return encrypt(root.toString())
    }

    @Synchronized
    fun importEncryptedPayload(payload: String): Int {
        val plain = decrypt(payload)
        if (plain.isBlank()) return 0
        return runCatching {
            val root = JSONObject(plain)
            if (root.optString("format") != EXPORT_FORMAT) return@runCatching 0
            val imported = recordsFromJson(root.optJSONArray("records") ?: JSONArray())
            imported.forEach(::upsert)
            imported.size
        }.getOrDefault(0)
    }

    private fun updateReviewState(id: String, state: MemoryReviewState): Boolean {
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return false
        records[index] = records[index].copy(
            reviewState = state,
            updatedAtMs = System.currentTimeMillis(),
            lastVerifiedAtMs = if (state == MemoryReviewState.CONFIRMED) {
                System.currentTimeMillis()
            } else {
                records[index].lastVerifiedAtMs
            }
        )
        writeRecords(records)
        return true
    }

    private fun readRecords(): List<StructuredMemoryRecord> {
        val plain = decrypt(prefs.getString(KEY_RECORDS, "").orEmpty())
        if (plain.isBlank()) return emptyList()
        return runCatching { recordsFromJson(JSONArray(plain)) }.getOrDefault(emptyList())
    }

    private fun writeRecords(records: List<StructuredMemoryRecord>) {
        prefs.edit().putString(KEY_RECORDS, encrypt(recordsToJson(records).toString())).apply()
    }

    private fun recordsToJson(records: List<StructuredMemoryRecord>): JSONArray = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("id", record.id)
                put("value", record.value)
                put("kind", record.kind.name)
                put("source", record.source.name)
                put("confidence", record.confidence.toDouble())
                put("namespace", record.namespace)
                put("sensitivity", record.sensitivity.name)
                put("retention", record.retention.name)
                put("reviewState", record.reviewState.name)
                put("sourceReference", record.sourceReference)
                put("createdAtMs", record.createdAtMs)
                put("updatedAtMs", record.updatedAtMs)
                put("lastVerifiedAtMs", record.lastVerifiedAtMs)
            })
        }
    }

    private fun recordsFromJson(array: JSONArray): List<StructuredMemoryRecord> = buildList {
        for (index in 0 until array.length()) {
            val root = array.optJSONObject(index) ?: continue
            add(
                StructuredMemoryRecord(
                    id = root.optString("id").ifBlank { UUID.randomUUID().toString() },
                    value = root.optString("value").take(StructuredMemoryRecord.MAX_VALUE_CHARS),
                    kind = enumOrDefault(root.optString("kind"), MemoryKind.EPISODE),
                    source = enumOrDefault(root.optString("source"), MemorySource.SYSTEM),
                    confidence = root.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
                    namespace = normalizeNamespace(root.optString("namespace")),
                    sensitivity = enumOrDefault(
                        root.optString("sensitivity"),
                        MemorySensitivity.NORMAL
                    ),
                    retention = enumOrDefault(
                        root.optString("retention"),
                        MemoryRetention.PERMANENT
                    ),
                    reviewState = enumOrDefault(
                        root.optString("reviewState"),
                        MemoryReviewState.PENDING_OWNER_REVIEW
                    ),
                    sourceReference = root.optString("sourceReference").take(240),
                    createdAtMs = root.optLong("createdAtMs", System.currentTimeMillis()),
                    updatedAtMs = root.optLong("updatedAtMs", System.currentTimeMillis()),
                    lastVerifiedAtMs = root.optLong("lastVerifiedAtMs", 0L)
                )
            )
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private fun normalizeNamespace(value: String): String = value
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "general" }
        .take(StructuredMemoryRecord.MAX_NAMESPACE_CHARS)

    private fun inferKind(value: String): MemoryKind = when {
        value.startsWith("CORRECTION", ignoreCase = true) -> MemoryKind.CORRECTION
        value.contains("project", ignoreCase = true) -> MemoryKind.PROJECT
        value.contains("prefer", ignoreCase = true) || value.contains("like", ignoreCase = true) ->
            MemoryKind.PREFERENCE
        value.contains("identity", ignoreCase = true) || value.startsWith("I am ", ignoreCase = true) ->
            MemoryKind.IDENTITY
        else -> MemoryKind.EPISODE
    }

    private fun inferNamespace(value: String): String = when {
        value.contains("FRIDAY", ignoreCase = true) || value.contains("Jarvis", ignoreCase = true) -> "friday"
        value.contains("NovaTopup", ignoreCase = true) -> "novatopup"
        value.contains("tournament", ignoreCase = true) -> "tournaments"
        value.contains("school", ignoreCase = true) -> "school"
        value.contains("MLBB", ignoreCase = true) || value.contains("gaming", ignoreCase = true) -> "gaming"
        else -> "general"
    }

    private fun fingerprint(value: String, namespace: String): String {
        val normalized = "${normalizeNamespace(namespace)}:${value.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            require(packed.size > IV_SIZE)
            val iv = packed.copyOfRange(0, IV_SIZE)
            val encrypted = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "friday_structured_memory_v1"
        private const val KEY_RECORDS = "records"
        private const val KEY_ALIAS = "friday_structured_memory_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val EXPORT_FORMAT = "friday-memory-v1"
    }
}
