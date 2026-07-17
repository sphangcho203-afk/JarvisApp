package com.seongja.jarvis

import android.content.Context

interface PcmCaptureObserver {
    fun onCaptureStarted(sampleRate: Int)
    fun onPcmChunk(buffer: ByteArray, count: Int)
    fun onCaptureFinished()
}

data class VoiceFamiliarityResult(
    val enrolled: Boolean,
    val score: Float,
    val label: String,
    val adapted: Boolean = false
) {
    fun diagnostic(): String = when {
        !enrolled -> "OWNER VOICE -> NOT ENROLLED"
        score >= .82f -> "OWNER VOICE -> FAMILIAR ${(score * 100).toInt()}%"
        score >= .58f -> "OWNER VOICE -> UNCERTAIN ${(score * 100).toInt()}%"
        else -> "OWNER VOICE -> LOW FAMILIARITY ${(score * 100).toInt()}%"
    }
}

class OwnerVoiceRuntime(context: Context) : PcmCaptureObserver {
    private val store = OwnerVoiceProfileStore(context.applicationContext)
    private var accumulator: PcmFeatureAccumulator? = null
    private var pendingSample: VoiceFeatureVector? = null

    @Synchronized
    override fun onCaptureStarted(sampleRate: Int) {
        accumulator = PcmFeatureAccumulator(sampleRate)
        pendingSample = null
    }

    @Synchronized
    override fun onPcmChunk(buffer: ByteArray, count: Int) {
        accumulator?.accept(buffer, count)
    }

    @Synchronized
    override fun onCaptureFinished() {
        pendingSample = accumulator?.finish()
        accumulator = null
    }

    @Synchronized
    fun completeTranscript(transcript: String): VoiceFamiliarityResult {
        val base = pendingSample
        pendingSample = null
        val profile = store.load()
        if (base == null || !base.isUsable()) {
            return VoiceFamiliarityResult(profile.enrolled, 0f, "insufficient_audio")
        }
        val words = transcript.trim().split(Regex("\\s+")).count(String::isNotBlank)
        val pace = if (base.durationMs >= 300L && words > 0) {
            (words * 60_000f / base.durationMs).coerceIn(0f, 300f)
        } else {
            0f
        }
        val sample = base.copy(wordsPerMinute = pace)
        if (!profile.enrolled) {
            return VoiceFamiliarityResult(false, 0f, "not_enrolled")
        }
        val score = OwnerVoiceMatcher.similarity(profile, sample)
        val canAdapt = store.deviceIsUnlocked() && score >= OwnerVoiceMatcher.ADAPTATION_MATCH_THRESHOLD
        if (canAdapt) {
            store.save(OwnerVoiceMatcher.adapt(profile, sample, transcript, score))
        } else {
            store.save(profile.copy(lastMatchScore = score))
        }
        val label = when {
            score >= .82f -> "familiar"
            score >= .58f -> "uncertain"
            else -> "low_familiarity"
        }
        return VoiceFamiliarityResult(true, score, label, canAdapt)
    }

    fun profile(): OwnerVoiceProfile = store.load()

    /**
     * Voice familiarity is never authorization. Sensitive actions still require
     * Android owner authentication, regardless of this score.
     */
    fun mayAuthorizeSensitiveAction(): Boolean = false
}
