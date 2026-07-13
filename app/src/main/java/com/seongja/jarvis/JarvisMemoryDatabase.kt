package com.seongja.jarvis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local persistent memory for Jarvis.
 *
 * Values are encrypted with an Android Keystore AES key before they enter
 * SQLite. The database never stores API keys and never syncs itself to GitHub.
 */
internal class JarvisMemoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    data class StoredContact(
        val kind: String,
        val label: String,
        val value: String,
        val isRecovery: Boolean
    )

    data class ConversationTurn(
        val role: String,
        val content: String,
        val createdAt: Long
    )

    private val cipher = JarvisMemoryCipher()
    private val lock = Any()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profile (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                sensitive INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                value TEXT NOT NULL,
                category TEXT NOT NULL,
                sensitive INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                kind TEXT NOT NULL,
                label TEXT NOT NULL,
                value TEXT NOT NULL,
                sensitive INTEGER NOT NULL DEFAULT 1,
                is_recovery INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX conversations_created_idx ON conversations(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first encrypted database. Future migrations must
        // preserve user data rather than dropping these tables.
    }

    fun upsertProfile(key: String, value: String, sensitive: Boolean = false) {
        val cleanKey = key.trim().lowercase(Locale.US)
        val cleanValue = value.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) return
        synchronized(lock) {
            val values = ContentValues().apply {
                put("key", cleanKey)
                put("value", cipher.encrypt(cleanValue))
                put("sensitive", if (sensitive) 1 else 0)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "profile",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun profileValue(key: String): String? = synchronized(lock) {
        readableDatabase.query(
            "profile",
            arrayOf("value"),
            "key = ?",
            arrayOf(key.trim().lowercase(Locale.US)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cipher.decrypt(cursor.getString(0))
        }
    }

    fun profileEntries(includeSensitive: Boolean): Map<String, String> = synchronized(lock) {
        val output = linkedMapOf<String, String>()
        val selection = if (includeSensitive) null else "sensitive = 0"
        readableDatabase.query(
            "profile",
            arrayOf("key", "value"),
            selection,
            null,
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                output[cursor.getString(0)] = cipher.decrypt(cursor.getString(1))
            }
        }
        output
    }

    fun addFact(value: String, category: String = "general", sensitive: Boolean = false) {
        val clean = value.trim().take(1_000)
        if (clean.isBlank()) return
        synchronized(lock) {
            val values = ContentValues().apply {
                put("fingerprint", fingerprint(clean))
                put("value", cipher.encrypt(clean))
                put("category", category.trim().ifBlank { "general" }.take(48))
                put("sensitive", if (sensitive) 1 else 0)
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "facts",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    fun facts(includeSensitive: Boolean = false, limit: Int = 80): List<String> = synchronized(lock) {
        val output = mutableListOf<String>()
        val selection = if (includeSensitive) null else "sensitive = 0"
        readableDatabase.query(
            "facts",
            arrayOf("value"),
            selection,
            null,
            null,
            null,
            "created_at DESC",
            limit.coerceIn(1, 500).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) output += cipher.decrypt(cursor.getString(0))
        }
        output
    }

    fun addContact(
        kind: String,
        label: String,
        value: String,
        isRecovery: Boolean = false
    ) {
        val clean = value.trim().take(240)
        if (clean.isBlank()) return
        synchronized(lock) {
            val values = ContentValues().apply {
                put("fingerprint", fingerprint("${kind.lowercase(Locale.US)}:$clean"))
                put("kind", kind.trim().lowercase(Locale.US).ifBlank { "other" })
                put("label", cipher.encrypt(label.trim().ifBlank { "Owner contact" }.take(80)))
                put("value", cipher.encrypt(clean))
                put("sensitive", 1)
                put("is_recovery", if (isRecovery) 1 else 0)
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "contacts",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun setRecoveryContact(value: String): Boolean = synchronized(lock) {
        val cleanFingerprintCandidates = listOf(
            fingerprint("phone:${value.trim()}"),
            fingerprint("email:${value.trim()}")
        )
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL("UPDATE contacts SET is_recovery = 0")
            var changed = 0
            cleanFingerprintCandidates.forEach { candidate ->
                val values = ContentValues().apply { put("is_recovery", 1) }
                changed += writableDatabase.update(
                    "contacts",
                    values,
                    "fingerprint = ?",
                    arrayOf(candidate)
                )
            }
            writableDatabase.setTransactionSuccessful()
            changed > 0
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun contacts(): List<StoredContact> = synchronized(lock) {
        val output = mutableListOf<StoredContact>()
        readableDatabase.query(
            "contacts",
            arrayOf("kind", "label", "value", "is_recovery"),
            null,
            null,
            null,
            null,
            "is_recovery DESC, created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                output += StoredContact(
                    kind = cursor.getString(0),
                    label = cipher.decrypt(cursor.getString(1)),
                    value = cipher.decrypt(cursor.getString(2)),
                    isRecovery = cursor.getInt(3) == 1
                )
            }
        }
        output
    }

    fun addConversation(role: String, content: String) {
        val clean = content.trim().take(12_000)
        if (clean.isBlank()) return
        synchronized(lock) {
            val values = ContentValues().apply {
                put("role", role.trim().lowercase(Locale.US).ifBlank { "unknown" }.take(24))
                put("content", cipher.encrypt(clean))
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.insert("conversations", null, values)
        }
    }

    fun recentConversations(limit: Int = 20): List<ConversationTurn> = synchronized(lock) {
        readConversationRows(limit.coerceIn(1, 500))
    }

    fun relevantConversations(query: String, limit: Int = 8): List<ConversationTurn> {
        val tokens = meaningfulTokens(query)
        val candidates = synchronized(lock) { readConversationRows(300) }
        if (tokens.isEmpty()) return candidates.take(limit.coerceIn(1, 30))

        return candidates
            .mapIndexed { index, turn ->
                val turnTokens = meaningfulTokens(turn.content)
                val overlap = tokens.intersect(turnTokens).size
                val phraseBonus = if (
                    query.length >= 8 && turn.content.contains(query, ignoreCase = true)
                ) 4 else 0
                val recencyBonus = (300 - index).coerceAtLeast(0) / 100.0
                turn to (overlap * 3.0 + phraseBonus + recencyBonus)
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { "${it.role}:${it.content}" }
            .take(limit.coerceIn(1, 30))
    }

    fun counts(): Triple<Int, Int, Int> = synchronized(lock) {
        Triple(countRows("facts"), countRows("contacts"), countRows("conversations"))
    }

    fun clearUserMemoryKeepIdentity() = synchronized(lock) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("facts", null, null)
            writableDatabase.delete("contacts", null, null)
            writableDatabase.delete("conversations", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun readConversationRows(limit: Int): List<ConversationTurn> {
        val output = mutableListOf<ConversationTurn>()
        readableDatabase.query(
            "conversations",
            arrayOf("role", "content", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                output += ConversationTurn(
                    role = cursor.getString(0),
                    content = cipher.decrypt(cursor.getString(1)),
                    createdAt = cursor.getLong(2)
                )
            }
        }
        return output
    }

    private fun countRows(table: String): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM $table",
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun meaningfulTokens(value: String): Set<String> = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun fingerprint(value: String): String {
        val normalized = value.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val DATABASE_NAME = "jarvis_memory.db"
        private const val DATABASE_VERSION = 1
        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "you", "your", "are",
            "was", "were", "have", "has", "had", "from", "what", "when", "where",
            "who", "why", "how", "can", "could", "would", "should", "about", "into"
        )
    }
}

private class JarvisMemoryCipher {
    private val keyAlias = "jarvis_memory_aes_v1"

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        return "v1:" + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith("v1:")) return stored
        return runCatching {
            val packed = Base64.decode(stored.removePrefix("v1:"), Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault("[encrypted memory unavailable]")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
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
        private const val GCM_IV_BYTES = 12
    }
}
