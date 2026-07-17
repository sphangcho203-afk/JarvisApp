package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class DiaryAiAccess {
    OWNER_ONLY,
    ASK_EVERY_TIME,
    SESSION_READABLE,
    MEMORY_APPROVED;

    fun label(): String = when (this) {
        OWNER_ONLY -> "OWNER ONLY // F.R.I.D.A.Y. BLOCKED"
        ASK_EVERY_TIME -> "ASK EACH TIME // TEMPORARY ACCESS"
        SESSION_READABLE -> "SESSION READABLE // MEMORY DISABLED"
        MEMORY_APPROVED -> "MEMORY APPROVED // OWNER CONFIRMATION REQUIRED"
    }

    fun fridayMayReadWithoutPrompt(): Boolean =
        this == SESSION_READABLE || this == MEMORY_APPROVED

    fun mayEnterMemory(): Boolean = this == MEMORY_APPROVED
}

data class PrivateDiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val aiAccess: DiaryAiAccess = DiaryAiAccess.OWNER_ONLY,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs
) {
    init {
        require(id.isNotBlank()) { "Diary entry ID cannot be blank." }
        require(title.length <= MAX_TITLE_CHARS) { "Diary title is too long." }
        require(body.length <= MAX_BODY_CHARS) { "Diary body is too long." }
        require(tags.size <= MAX_TAGS) { "Too many diary tags." }
    }

    fun searchableText(): String = buildString {
        append(title)
        append('\n')
        append(body)
        append('\n')
        append(tags.joinToString(" "))
    }

    companion object {
        const val MAX_TITLE_CHARS = 180
        const val MAX_BODY_CHARS = 120_000
        const val MAX_TAGS = 24
    }
}

data class DiaryAccessEvent(
    val entryId: String,
    val action: String,
    val occurredAtMs: Long = System.currentTimeMillis(),
    val detail: String = ""
)

class PrivateDiaryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun listEntries(): List<PrivateDiaryEntry> = readEntries()
        .sortedByDescending(PrivateDiaryEntry::updatedAtMs)

    @Synchronized
    fun findById(id: String): PrivateDiaryEntry? =
        readEntries().firstOrNull { it.id == id }

    @Synchronized
    fun searchOwnerView(query: String): List<PrivateDiaryEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return listEntries()
        return listEntries().filter { it.searchableText().lowercase().contains(normalized) }
    }

    @Synchronized
    fun searchReadableByFriday(query: String): List<PrivateDiaryEntry> =
        searchOwnerView(query).filter { it.aiAccess.fridayMayReadWithoutPrompt() }

    @Synchronized
    fun upsert(entry: PrivateDiaryEntry): PrivateDiaryEntry {
        val clean = entry.copy(
            title = entry.title.trim().take(PrivateDiaryEntry.MAX_TITLE_CHARS),
            body = entry.body.trimEnd().take(PrivateDiaryEntry.MAX_BODY_CHARS),
            tags = entry.tags
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(PrivateDiaryEntry.MAX_TAGS),
            updatedAtMs = System.currentTimeMillis()
        )
        val entries = readEntries().toMutableList()
        val existingIndex = entries.indexOfFirst { it.id == clean.id }
        if (existingIndex >= 0) {
            entries[existingIndex] = clean.copy(createdAtMs = entries[existingIndex].createdAtMs)
        } else {
            entries += clean
        }
        writeEntries(entries)
        recordAccess(clean.id, "OWNER_SAVE", "access=${clean.aiAccess.name}")
        return clean
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val entries = readEntries().toMutableList()
        val removed = entries.removeAll { it.id == id }
        if (removed) {
            writeEntries(entries)
            recordAccess(id, "OWNER_DELETE")
        }
        return removed
    }

    @Synchronized
    fun recordAccess(entryId: String, action: String, detail: String = "") {
        val events = readAudit().toMutableList()
        events += DiaryAccessEvent(
            entryId = entryId.take(80),
            action = action.trim().uppercase().take(80),
            detail = detail.replace(Regex("\\s+"), " ").trim().take(240)
        )
        writeAudit(events.takeLast(MAX_AUDIT_EVENTS))
    }

    @Synchronized
    fun auditFor(entryId: String? = null): List<DiaryAccessEvent> = readAudit()
        .asSequence()
        .filter { entryId == null || it.entryId == entryId }
        .sortedByDescending(DiaryAccessEvent::occurredAtMs)
        .toList()

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun readEntries(): List<PrivateDiaryEntry> {
        val decrypted = decrypt(prefs.getString(KEY_ENTRIES, "").orEmpty())
        if (decrypted.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(decrypted)
            buildList {
                for (index in 0 until array.length()) {
                    val root = array.optJSONObject(index) ?: continue
                    add(root.toEntry())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(entries: List<PrivateDiaryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ENTRIES, encrypt(array.toString())).apply()
    }

    private fun readAudit(): List<DiaryAccessEvent> {
        val decrypted = decrypt(prefs.getString(KEY_AUDIT, "").orEmpty())
        if (decrypted.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(decrypted)
            buildList {
                for (index in 0 until array.length()) {
                    val root = array.optJSONObject(index) ?: continue
                    add(
                        DiaryAccessEvent(
                            entryId = root.optString("entryId"),
                            action = root.optString("action"),
                            occurredAtMs = root.optLong("occurredAtMs"),
                            detail = root.optString("detail")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAudit(events: List<DiaryAccessEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONObject().apply {
                put("entryId", event.entryId)
                put("action", event.action)
                put("occurredAtMs", event.occurredAtMs)
                put("detail", event.detail)
            })
        }
        prefs.edit().putString(KEY_AUDIT, encrypt(array.toString())).apply()
    }

    private fun PrivateDiaryEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("tags", JSONArray(tags))
        put("aiAccess", aiAccess.name)
        put("createdAtMs", createdAtMs)
        put("updatedAtMs", updatedAtMs)
    }

    private fun JSONObject.toEntry(): PrivateDiaryEntry {
        val tagsArray = optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (index in 0 until tagsArray.length()) {
                tagsArray.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return PrivateDiaryEntry(
            id = optString("id").ifBlank { UUID.randomUUID().toString() },
            title = optString("title").take(PrivateDiaryEntry.MAX_TITLE_CHARS),
            body = optString("body").take(PrivateDiaryEntry.MAX_BODY_CHARS),
            tags = tags.take(PrivateDiaryEntry.MAX_TAGS),
            aiAccess = runCatching {
                DiaryAiAccess.valueOf(optString("aiAccess"))
            }.getOrDefault(DiaryAiAccess.OWNER_ONLY),
            createdAtMs = optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = optLong("updatedAtMs", System.currentTimeMillis())
        )
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + ciphertext.size)
        System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
        System.arraycopy(ciphertext, 0, payload, cipher.iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_SIZE)
            val iv = payload.copyOfRange(0, IV_SIZE)
            val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
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
        private const val PREFS_NAME = "friday_private_diary_encrypted_v1"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_AUDIT = "audit"
        private const val KEY_ALIAS = "friday_private_diary_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val MAX_AUDIT_EVENTS = 500
    }
}
