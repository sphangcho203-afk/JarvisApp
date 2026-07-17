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

enum class MemoryKind { PREFERENCE, PROJECT, EPISODE, IDENTITY, CONVERSATION, TEMPORARY, CORRECTION }
enum class MemorySource { OWNER_DIRECT, OWNER_CONFIRMED, REPEATED_PATTERN, DIARY_APPROVAL, CONVERSATION_ARCHIVE, LEGACY_IMPORT, SYSTEM }
enum class MemorySensitivity { NORMAL, PRIVATE, HIGHLY_SENSITIVE }
enum class MemoryRetention { SESSION, THIRTY_DAYS, ONE_YEAR, PERMANENT }
enum class MemoryReviewState { PENDING_OWNER_REVIEW, CONFIRMED, REJECTED }

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
        require(id.isNotBlank())
        require(value.length <= MAX_VALUE_CHARS)
        require(namespace.length <= MAX_NAMESPACE_CHARS)
        require(confidence in 0f..1f)
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = when (retention) {
        MemoryRetention.SESSION -> false
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
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sessionRecords = linkedMapOf<String, StructuredMemoryRecord>()

    @Synchronized
    fun list(namespace: String? = null, includeRejected: Boolean = false, includeExpired: Boolean = false): List<StructuredMemoryRecord> {
        val now = System.currentTimeMillis()
        val ns = namespace?.let(::normalizeNamespace)
        return (readRecords() + sessionRecords.values)
            .asSequence()
            .filter { includeRejected || it.reviewState != MemoryReviewState.REJECTED }
            .filter { includeExpired || !it.isExpired(now) }
            .filter { ns.isNullOrBlank() || it.namespace == ns }
            .distinctBy(StructuredMemoryRecord::id)
            .sortedByDescending(StructuredMemoryRecord::updatedAtMs)
            .toList()
    }

    @Synchronized
    fun pendingReview(): List<StructuredMemoryRecord> = list().filter { it.reviewState == MemoryReviewState.PENDING_OWNER_REVIEW }

    @Synchronized
    fun find(id: String): StructuredMemoryRecord? = sessionRecords[id] ?: readRecords().firstOrNull { it.id == id }

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
        if (clean.retention == MemoryRetention.SESSION) {
            sessionRecords[clean.id] = clean
            return clean
        }

        val records = readRecords().toMutableList()
        val existing = records.indexOfFirst { it.id == clean.id }
        val duplicate = records.indexOfFirst {
            fingerprint(it.value, it.namespace) == fingerprint(clean.value, clean.namespace)
        }
        when {
            existing >= 0 -> records[existing] = clean.copy(createdAtMs = records[existing].createdAtMs)
            duplicate >= 0 -> records[duplicate] = clean.copy(id = records[duplicate].id, createdAtMs = records[duplicate].createdAtMs)
            else -> records += clean
        }
        writeRecords(records)
        return clean
    }

    @Synchronized fun confirm(id: String): Boolean = updateReviewState(id, MemoryReviewState.CONFIRMED)
    @Synchronized fun reject(id: String): Boolean = updateReviewState(id, MemoryReviewState.REJECTED)

    @Synchronized
    fun delete(id: String): Boolean {
        val sessionChanged = sessionRecords.remove(id) != null
        val records = readRecords().toMutableList()
        val persistentChanged = records.removeAll { it.id == id }
        if (persistentChanged) writeRecords(records)
        return sessionChanged || persistentChanged
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

    @Synchronized fun clearSession() { sessionRecords.clear() }
    @Synchronized fun clearAll() { sessionRecords.clear(); prefs.edit().clear().apply() }

    @Synchronized
    fun importLegacy(vault: MemoryVault): Int {
        val records = readRecords().toMutableList()
        val fingerprints = records.mapTo(mutableSetOf()) { fingerprint(it.value, it.namespace) }
        var added = 0
        vault.facts(includeSensitive = false).forEach { fact ->
            val clean = fact.trim()
            if (clean.isBlank()) return@forEach
            val namespace = inferNamespace(clean)
            if (fingerprints.add(fingerprint(clean, namespace))) {
                records += StructuredMemoryRecord(
                    value = clean,
                    kind = inferKind(clean),
                    source = MemorySource.LEGACY_IMPORT,
                    confidence = .75f,
                    namespace = namespace,
                    sensitivity = MemorySensitivity.NORMAL,
                    retention = MemoryRetention.PERMANENT,
                    reviewState = MemoryReviewState.CONFIRMED,
                    sourceReference = "legacy_memory_vault"
                )
                added++
            }
        }
        if (added > 0) writeRecords(records)
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
    fun exportEncryptedPayload(): String = encrypt(
        JSONObject().apply {
            put("format", EXPORT_FORMAT)
            put("createdAtMs", System.currentTimeMillis())
            put("records", recordsToJson(readRecords()))
        }.toString()
    )

    @Synchronized
    fun importEncryptedPayload(payload: String): Int {
        val plain = decrypt(payload)
        if (plain.isBlank()) return 0
        return runCatching {
            val root = JSONObject(plain)
            if (root.optString("format") != EXPORT_FORMAT) return@runCatching 0
            val imported = recordsFromJson(root.optJSONArray("records") ?: JSONArray())
            imported.filter { it.retention != MemoryRetention.SESSION }.forEach(::upsert)
            imported.size
        }.getOrDefault(0)
    }

    private fun updateReviewState(id: String, state: MemoryReviewState): Boolean {
        sessionRecords[id]?.let {
            sessionRecords[id] = it.copy(reviewState = state, updatedAtMs = System.currentTimeMillis())
            return true
        }
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return false
        val now = System.currentTimeMillis()
        records[index] = records[index].copy(
            reviewState = state,
            updatedAtMs = now,
            lastVerifiedAtMs = if (state == MemoryReviewState.CONFIRMED) now else records[index].lastVerifiedAtMs
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

    private fun recordsToJson(records: List<StructuredMemoryRecord>) = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("id", record.id); put("value", record.value); put("kind", record.kind.name)
                put("source", record.source.name); put("confidence", record.confidence.toDouble())
                put("namespace", record.namespace); put("sensitivity", record.sensitivity.name)
                put("retention", record.retention.name); put("reviewState", record.reviewState.name)
                put("sourceReference", record.sourceReference); put("createdAtMs", record.createdAtMs)
                put("updatedAtMs", record.updatedAtMs); put("lastVerifiedAtMs", record.lastVerifiedAtMs)
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
                    sensitivity = enumOrDefault(root.optString("sensitivity"), MemorySensitivity.NORMAL),
                    retention = enumOrDefault(root.optString("retention"), MemoryRetention.PERMANENT),
                    reviewState = enumOrDefault(root.optString("reviewState"), MemoryReviewState.PENDING_OWNER_REVIEW),
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

    private fun normalizeNamespace(value: String): String = value.trim().lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]"), "_").replace(Regex("_+"), "_").trim('_')
        .ifBlank { "general" }.take(StructuredMemoryRecord.MAX_NAMESPACE_CHARS)

    private fun inferKind(value: String): MemoryKind = when {
        value.startsWith("CORRECTION", true) -> MemoryKind.CORRECTION
        value.contains("project", true) -> MemoryKind.PROJECT
        value.contains("prefer", true) || value.contains("like", true) -> MemoryKind.PREFERENCE
        value.contains("identity", true) || value.startsWith("I am ", true) -> MemoryKind.IDENTITY
        else -> MemoryKind.EPISODE
    }

    private fun inferNamespace(value: String): String = when {
        value.contains("FRIDAY", true) || value.contains("Jarvis", true) -> "friday"
        value.contains("NovaTopup", true) -> "novatopup"
        value.contains("tournament", true) -> "tournaments"
        value.contains("school", true) -> "school"
        value.contains("MLBB", true) || value.contains("gaming", true) -> "gaming"
        else -> "general"
    }

    private fun fingerprint(value: String, namespace: String): String {
        val normalized = "${normalizeNamespace(namespace)}:${value.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()}"
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            require(packed.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, IV_SIZE)))
            String(cipher.doFinal(packed.copyOfRange(IV_SIZE, packed.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
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
