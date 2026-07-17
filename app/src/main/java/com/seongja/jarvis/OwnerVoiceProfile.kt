package com.seongja.jarvis

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs
import kotlin.math.sqrt

data class VoiceFeatureVector(
    val rms: Float,
    val meanAbsoluteAmplitude: Float,
    val zeroCrossingRate: Float,
    val crestFactor: Float,
    val voicedRatio: Float,
    val durationMs: Long,
    val wordsPerMinute: Float = 0f
) {
    fun values(): FloatArray = floatArrayOf(
        rms,
        meanAbsoluteAmplitude,
        zeroCrossingRate,
        crestFactor,
        voicedRatio,
        (durationMs.coerceIn(0L, 20_000L) / 20_000f),
        (wordsPerMinute.coerceIn(0f, 300f) / 300f)
    )

    fun isUsable(): Boolean = durationMs >= 450L && rms > .004f && voicedRatio > .01f
}

data class OwnerVoiceProfile(
    val enrolled: Boolean = false,
    val centroid: List<Float> = emptyList(),
    val sampleCount: Int = 0,
    val enrolledAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val lastMatchScore: Float = 0f,
    val commonPhrases: List<String> = emptyList(),
    val averageWordsPerMinute: Float = 0f
) {
    fun statusLabel(): String = when {
        !enrolled -> "NOT ENROLLED"
        sampleCount < 4 -> "CALIBRATING // $sampleCount SAMPLES"
        else -> "ENROLLED // $sampleCount VERIFIED SAMPLES"
    }
}

class PcmFeatureAccumulator(private val sampleRate: Int = 16_000) {
    private var sampleCount = 0L
    private var sumSquares = 0.0
    private var sumAbsolute = 0.0
    private var peak = 0.0
    private var zeroCrossings = 0L
    private var voicedSamples = 0L
    private var previousSample = 0
    private var hasPrevious = false

    fun accept(buffer: ByteArray, count: Int = buffer.size) {
        var index = 0
        val limit = count.coerceAtMost(buffer.size)
        while (index + 1 < limit) {
            val low = buffer[index].toInt() and 0xff
            val high = buffer[index + 1].toInt()
            val raw = ((high shl 8) or low).toShort().toInt()
            val normalized = raw / 32768.0
            val magnitude = abs(normalized)
            sumSquares += normalized * normalized
            sumAbsolute += magnitude
            if (magnitude > peak) peak = magnitude
            if (magnitude >= VOICED_THRESHOLD) voicedSamples++
            if (hasPrevious && ((previousSample < 0 && raw >= 0) || (previousSample >= 0 && raw < 0))) {
                zeroCrossings++
            }
            previousSample = raw
            hasPrevious = true
            sampleCount++
            index += 2
        }
    }

    fun finish(transcript: String = ""): VoiceFeatureVector {
        if (sampleCount <= 0L) return VoiceFeatureVector(0f, 0f, 0f, 0f, 0f, 0L, 0f)
        val rms = sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
        val meanAbs = (sumAbsolute / sampleCount).toFloat().coerceIn(0f, 1f)
        val zcr = (zeroCrossings.toDouble() / sampleCount).toFloat().coerceIn(0f, 1f)
        val crest = if (rms <= .0001f) 0f else (peak / rms).toFloat().coerceIn(0f, 12f) / 12f
        val voiced = (voicedSamples.toDouble() / sampleCount).toFloat().coerceIn(0f, 1f)
        val duration = (sampleCount * 1_000L / sampleRate.coerceAtLeast(1)).coerceAtLeast(0L)
        val words = transcript.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val wpm = if (duration >= 300L && words > 0) words * 60_000f / duration else 0f
        return VoiceFeatureVector(rms, meanAbs, zcr, crest, voiced, duration, wpm.coerceIn(0f, 300f))
    }

    companion object {
        private const val VOICED_THRESHOLD = .018
    }
}

object OwnerVoiceMatcher {
    private val tolerances = floatArrayOf(.10f, .08f, .12f, .20f, .28f, .35f, .30f)
    private val weights = floatArrayOf(.18f, .14f, .14f, .10f, .18f, .08f, .18f)

    fun similarity(profile: OwnerVoiceProfile, sample: VoiceFeatureVector): Float {
        if (!profile.enrolled || profile.centroid.size != sample.values().size || !sample.isUsable()) return 0f
        val values = sample.values()
        var weightedDistance = 0f
        var totalWeight = 0f
        values.indices.forEach { index ->
            val tolerance = tolerances[index].coerceAtLeast(.01f)
            val distance = (abs(values[index] - profile.centroid[index]) / tolerance).coerceIn(0f, 1f)
            weightedDistance += distance * weights[index]
            totalWeight += weights[index]
        }
        return (1f - weightedDistance / totalWeight.coerceAtLeast(.01f)).coerceIn(0f, 1f)
    }

    fun build(samples: List<VoiceFeatureVector>, phrases: List<String>): OwnerVoiceProfile {
        val usable = samples.filter(VoiceFeatureVector::isUsable)
        if (usable.isEmpty()) return OwnerVoiceProfile()
        val dimensions = usable.first().values().size
        val centroid = MutableList(dimensions) { 0f }
        usable.forEach { sample ->
            sample.values().forEachIndexed { index, value -> centroid[index] += value }
        }
        centroid.indices.forEach { centroid[it] /= usable.size }
        val now = System.currentTimeMillis()
        return OwnerVoiceProfile(
            enrolled = usable.size >= MIN_ENROLLMENT_SAMPLES,
            centroid = centroid,
            sampleCount = usable.size,
            enrolledAtMs = now,
            updatedAtMs = now,
            commonPhrases = normalizePhrases(phrases),
            averageWordsPerMinute = usable.map(VoiceFeatureVector::wordsPerMinute)
                .filter { it > 0f }
                .average()
                .takeIf { !it.isNaN() }
                ?.toFloat()
                ?: 0f
        )
    }

    fun adapt(profile: OwnerVoiceProfile, sample: VoiceFeatureVector, phrase: String, match: Float): OwnerVoiceProfile {
        if (!profile.enrolled || !sample.isUsable() || match < ADAPTATION_MATCH_THRESHOLD) {
            return profile.copy(lastMatchScore = match)
        }
        val current = profile.centroid
        val incoming = sample.values()
        if (current.size != incoming.size) return profile
        val alpha = (1f / (profile.sampleCount + 1f)).coerceIn(.005f, .025f)
        val next = current.indices.map { index -> current[index] * (1f - alpha) + incoming[index] * alpha }
        val phrases = normalizePhrases(profile.commonPhrases + phrase)
        val pace = when {
            sample.wordsPerMinute <= 0f -> profile.averageWordsPerMinute
            profile.averageWordsPerMinute <= 0f -> sample.wordsPerMinute
            else -> profile.averageWordsPerMinute * (1f - alpha) + sample.wordsPerMinute * alpha
        }
        return profile.copy(
            centroid = next,
            sampleCount = (profile.sampleCount + 1).coerceAtMost(100_000),
            updatedAtMs = System.currentTimeMillis(),
            lastMatchScore = match,
            commonPhrases = phrases,
            averageWordsPerMinute = pace
        )
    }

    private fun normalizePhrases(values: List<String>): List<String> = values
        .map { it.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").take(100) }
        .filter { it.length >= 2 }
        .distinct()
        .takeLast(40)

    const val MIN_ENROLLMENT_SAMPLES = 4
    const val ADAPTATION_MATCH_THRESHOLD = .82f
}

class OwnerVoiceProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): OwnerVoiceProfile {
        val raw = decrypt(prefs.getString(KEY_PROFILE, "").orEmpty())
        if (raw.isBlank()) return OwnerVoiceProfile()
        return runCatching {
            val root = JSONObject(raw)
            OwnerVoiceProfile(
                enrolled = root.optBoolean("enrolled", false),
                centroid = root.optJSONArray("centroid").toFloatList(),
                sampleCount = root.optInt("sampleCount", 0),
                enrolledAtMs = root.optLong("enrolledAtMs", 0L),
                updatedAtMs = root.optLong("updatedAtMs", 0L),
                lastMatchScore = root.optDouble("lastMatchScore", 0.0).toFloat().coerceIn(0f, 1f),
                commonPhrases = root.optJSONArray("commonPhrases").toStringList(),
                averageWordsPerMinute = root.optDouble("averageWordsPerMinute", 0.0).toFloat().coerceIn(0f, 300f)
            )
        }.getOrDefault(OwnerVoiceProfile())
    }

    @Synchronized
    fun save(profile: OwnerVoiceProfile) {
        val root = JSONObject().apply {
            put("enrolled", profile.enrolled)
            put("centroid", JSONArray(profile.centroid))
            put("sampleCount", profile.sampleCount)
            put("enrolledAtMs", profile.enrolledAtMs)
            put("updatedAtMs", profile.updatedAtMs)
            put("lastMatchScore", profile.lastMatchScore.toDouble())
            put("commonPhrases", JSONArray(profile.commonPhrases))
            put("averageWordsPerMinute", profile.averageWordsPerMinute.toDouble())
        }
        prefs.edit().putString(KEY_PROFILE, encrypt(root.toString())).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun deviceIsUnlocked(): Boolean {
        val keyguard = appContext.getSystemService(KeyguardManager::class.java)
        return keyguard?.isDeviceLocked == false
    }

    private fun JSONArray?.toFloatList(): List<Float> {
        if (this == null) return emptyList()
        return buildList { for (index in 0 until length()) add(optDouble(index, 0.0).toFloat()) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
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
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, packed.copyOfRange(0, IV_SIZE))
            )
            String(cipher.doFinal(packed.copyOfRange(IV_SIZE, packed.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "friday_owner_voice_profile_v1"
        private const val KEY_PROFILE = "profile"
        private const val KEY_ALIAS = "friday_owner_voice_profile_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
