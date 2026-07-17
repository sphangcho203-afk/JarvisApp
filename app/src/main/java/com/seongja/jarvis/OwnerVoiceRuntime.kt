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

object OwnerVoiceAuthorizationPolicy {
    /** Acoustic familiarity never grants privileged access. */
    fun mayAuthorizeSensitiveAction(): Boolean = false
}

class OwnerVoiceRuntime(context: Context) : PcmCaptureObserver {
    private val store = OwnerVoiceProfileStore(context.applicationContext)
    private var accumulator: PcmFeatureAccumulator? = null
    private var lastResult = VoiceFamiliarityResult(false, 0f, "not_enrolled")

    @Synchronized
    override fun onCaptureStarted(sampleRate: Int) {
        accumulator = PcmFeatureAccumulator(sampleRate)
    }

    @Synchronized
    override fun onPcmChunk(buffer: ByteArray, count: Int) {
        accumulator?.accept(buffer, count)
    }

    @Synchronized
    override fun onCaptureFinished() {
        val sample = accumulator?.finish()
        accumulator = null
        lastResult = evaluateAndAdapt(sample, phrase = "")
    }

    @Synchronized
    fun completeTranscript(transcript: String): VoiceFamiliarityResult {
        val current = store.load()
        if (!current.enrolled || transcript.isBlank()) return lastResult
        if (store.deviceIsUnlocked() && lastResult.score >= OwnerVoiceMatcher.ADAPTATION_MATCH_THRESHOLD) {
            store.save(
                current.copy(
                    commonPhrases = (current.commonPhrases + transcript.trim().lowercase())
                        .map { it.replace(Regex("\\s+"), " ").take(100) }
                        .filter { it.length >= 2 }
                        .distinct()
                        .takeLast(40),
                    updatedAtMs = System.currentTimeMillis(),
                    lastMatchScore = lastResult.score
                )
            )
        }
        return lastResult
    }

    @Synchronized
    fun latestResult(): VoiceFamiliarityResult = lastResult

    fun profile(): OwnerVoiceProfile = store.load()

    fun mayAuthorizeSensitiveAction(): Boolean =
        OwnerVoiceAuthorizationPolicy.mayAuthorizeSensitiveAction()

    private fun evaluateAndAdapt(sample: VoiceFeatureVector?, phrase: String): VoiceFamiliarityResult {
        val profile = store.load()
        if (sample == null || !sample.isUsable()) {
            return VoiceFamiliarityResult(profile.enrolled, 0f, "insufficient_audio")
        }
        if (!profile.enrolled) return VoiceFamiliarityResult(false, 0f, "not_enrolled")
        val score = OwnerVoiceMatcher.similarity(profile, sample)
        val canAdapt = store.deviceIsUnlocked() && score >= OwnerVoiceMatcher.ADAPTATION_MATCH_THRESHOLD
        if (canAdapt) {
            store.save(OwnerVoiceMatcher.adapt(profile, sample, phrase, score))
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
}
