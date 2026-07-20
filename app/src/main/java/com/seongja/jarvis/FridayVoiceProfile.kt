package com.seongja.jarvis

import android.content.Context
import android.speech.tts.Voice
import java.util.Locale

data class FridayVoiceProfile(
    val enginePackage: String = "",
    val voiceName: String = "",
    val localeTag: String = Locale.US.toLanguageTag(),
    val speechRate: Float = DEFAULT_RATE,
    val pitch: Float = DEFAULT_PITCH,
    val preferOffline: Boolean = true
) {
    companion object {
        const val DEFAULT_RATE = 0.94f
        const val DEFAULT_PITCH = 1.02f
    }
}

object FridayVoiceStore {
    private const val PREFS = "friday_voice_lab"
    private const val KEY_ENGINE = "engine_package"
    private const val KEY_VOICE = "voice_name"
    private const val KEY_LOCALE = "locale_tag"
    private const val KEY_RATE = "speech_rate"
    private const val KEY_PITCH = "pitch"
    private const val KEY_OFFLINE = "prefer_offline"

    fun load(context: Context): FridayVoiceProfile {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FridayVoiceProfile(
            enginePackage = prefs.getString(KEY_ENGINE, "").orEmpty(),
            voiceName = prefs.getString(KEY_VOICE, "").orEmpty(),
            localeTag = prefs.getString(KEY_LOCALE, Locale.US.toLanguageTag())
                .orEmpty()
                .ifBlank { Locale.US.toLanguageTag() },
            speechRate = prefs.getFloat(KEY_RATE, FridayVoiceProfile.DEFAULT_RATE)
                .coerceIn(0.70f, 1.30f),
            pitch = prefs.getFloat(KEY_PITCH, FridayVoiceProfile.DEFAULT_PITCH)
                .coerceIn(0.80f, 1.20f),
            preferOffline = prefs.getBoolean(KEY_OFFLINE, true)
        )
    }

    fun save(context: Context, profile: FridayVoiceProfile) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, profile.enginePackage)
            .putString(KEY_VOICE, profile.voiceName)
            .putString(KEY_LOCALE, profile.localeTag)
            .putFloat(KEY_RATE, profile.speechRate.coerceIn(0.70f, 1.30f))
            .putFloat(KEY_PITCH, profile.pitch.coerceIn(0.80f, 1.20f))
            .putBoolean(KEY_OFFLINE, profile.preferOffline)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

object FridayVoiceSelector {
    fun englishVoices(voices: Set<Voice>): List<Voice> = voices
        .filter { it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) }
        .sortedWith(
            compareBy<Voice>(
                { it.isNetworkConnectionRequired },
                { localeRank(it.locale) },
                { -it.quality },
                { it.latency },
                { it.name.lowercase(Locale.US) }
            )
        )

    fun select(voices: Set<Voice>, profile: FridayVoiceProfile): Voice? {
        val english = englishVoices(voices)
        if (english.isEmpty()) return null

        english.firstOrNull { it.name == profile.voiceName }?.let { return it }

        val requestedLocale = Locale.forLanguageTag(profile.localeTag)
        return english
            .asSequence()
            .filter { !profile.preferOffline || !it.isNetworkConnectionRequired }
            .sortedWith(
                compareBy<Voice>(
                    { localeDistance(it.locale, requestedLocale) },
                    { -it.quality },
                    { it.latency },
                    { it.name.lowercase(Locale.US) }
                )
            )
            .firstOrNull()
            ?: english.first()
    }

    private fun localeRank(locale: Locale): Int = when {
        locale.country.equals("GB", ignoreCase = true) -> 0
        locale.country.equals("US", ignoreCase = true) -> 1
        locale.country.equals("AU", ignoreCase = true) -> 2
        locale.country.equals("IN", ignoreCase = true) -> 3
        else -> 4
    }

    private fun localeDistance(candidate: Locale, requested: Locale): Int = when {
        candidate.toLanguageTag().equals(requested.toLanguageTag(), ignoreCase = true) -> 0
        candidate.language.equals(requested.language, ignoreCase = true) &&
            candidate.country.equals(requested.country, ignoreCase = true) -> 1
        candidate.language.equals(requested.language, ignoreCase = true) -> 2
        else -> 3
    }
}

object FridayVoiceLabCommand {
    fun matches(input: String): Boolean {
        val normalized = input
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized in setOf("open voice lab", "voice lab", "friday voice lab")) return true

        val mentionsVoice = listOf("voice", "speech", "tts").any(normalized::contains)
        val setupIntent = listOf(
            "open",
            "setup",
            "set up",
            "settings",
            "configure",
            "choose",
            "change",
            "test",
            "audition"
        ).any(normalized::contains)
        return mentionsVoice && setupIntent
    }
}
