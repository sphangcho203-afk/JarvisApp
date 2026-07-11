package com.seongja.jarvis

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale
import kotlin.math.ceil

internal data class CountdownSnapshot(
    val active: Boolean,
    val label: String,
    val remainingMs: Long,
    val totalMs: Long
) {
    val progress: Float
        get() = if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    val remainingSeconds: Long
        get() = ceil(remainingMs / 1000.0).toLong().coerceAtLeast(0L)
}

internal class JarvisCountdownController(
    private val onTick: (CountdownSnapshot) -> Unit,
    private val onFinished: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var endElapsedRealtime = 0L
    private var totalMs = 0L
    private var label = "MISSION TIMER"
    private var active = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return

            val remaining = (endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            onTick(snapshot(remaining))

            if (remaining <= 0L) {
                active = false
                val completedLabel = label
                totalMs = 0L
                onTick(snapshot(0L))
                onFinished(completedLabel)
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start(durationMs: Long, timerLabel: String = "MISSION TIMER"): CountdownSnapshot {
        val safeDuration = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        handler.removeCallbacks(ticker)
        totalMs = safeDuration
        endElapsedRealtime = SystemClock.elapsedRealtime() + safeDuration
        label = timerLabel.take(28).ifBlank { "MISSION TIMER" }
        active = true
        val current = snapshot(safeDuration)
        onTick(current)
        handler.postDelayed(ticker, TICK_MS)
        return current
    }

    fun cancel(): CountdownSnapshot {
        handler.removeCallbacks(ticker)
        active = false
        totalMs = 0L
        val current = snapshot(0L)
        onTick(current)
        return current
    }

    fun current(): CountdownSnapshot {
        val remaining = if (active) {
            (endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            0L
        }
        return snapshot(remaining)
    }

    fun destroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun snapshot(remaining: Long): CountdownSnapshot = CountdownSnapshot(
        active = active,
        label = label,
        remainingMs = remaining,
        totalMs = totalMs
    )

    companion object {
        private const val TICK_MS = 100L
        private const val MIN_DURATION_MS = 1_000L
        private const val MAX_DURATION_MS = 24L * 60L * 60L * 1_000L
    }
}

internal sealed class CountdownCommand {
    data class Start(val durationMs: Long) : CountdownCommand()
    data object Cancel : CountdownCommand()
    data object Status : CountdownCommand()
    data class Invalid(val reason: String) : CountdownCommand()
}

internal object CountdownCommandParser {
    private val cancelTerms = listOf(
        "cancel timer", "cancel countdown", "stop timer", "stop countdown",
        "clear timer", "clear countdown", "delete timer"
    )
    private val statusTerms = listOf(
        "timer status", "countdown status", "how much time", "time remaining",
        "how long is left", "what is left on the timer"
    )

    private val numericUnit = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|hr|minutes?|mins?|min|seconds?|secs?|sec)",
        RegexOption.IGNORE_CASE
    )
    private val numberWordPattern =
        "(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|" +
            "fifteen|sixteen|seventeen|eighteen|nineteen|" +
            "twenty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|" +
            "thirty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|" +
            "forty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|" +
            "fifty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|sixty|ninety)"
    private val wordUnit = Regex(
        "\\b($numberWordPattern)\\s+(hours?|hrs?|hr|minutes?|mins?|min|seconds?|secs?|sec)\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): CountdownCommand? {
        val input = raw.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        val mentionsTimer = input.contains("timer") || input.contains("countdown") || statusTerms.any(input::contains)
        if (!mentionsTimer) return null

        if (cancelTerms.any(input::contains)) return CountdownCommand.Cancel
        if (statusTerms.any(input::contains) || input == "timer" || input == "countdown") {
            return CountdownCommand.Status
        }

        var durationMs = 0.0
        numericUnit.findAll(input).forEach { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
            durationMs += amount * unitMs(match.groupValues[2])
        }

        wordUnit.findAll(input).forEach { match ->
            val amount = wordNumber(match.groupValues[1]) ?: 0
            durationMs += amount * unitMs(match.groupValues[2])
        }

        if (durationMs <= 0.0) {
            val bareNumber = Regex("\\b(\\d+)\\b").find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (bareNumber != null && input.contains("countdown")) {
                durationMs = bareNumber * 1_000.0
            }
        }

        if (durationMs <= 0.0) {
            return CountdownCommand.Invalid("Say a duration, for example: set a timer for five minutes.")
        }

        val finalDuration = durationMs.toLong()
        if (finalDuration > 24L * 60L * 60L * 1_000L) {
            return CountdownCommand.Invalid("The maximum countdown is twenty-four hours.")
        }
        return CountdownCommand.Start(finalDuration)
    }

    private fun unitMs(unit: String): Double = when {
        unit.startsWith("h", ignoreCase = true) -> 3_600_000.0
        unit.startsWith("m", ignoreCase = true) -> 60_000.0
        else -> 1_000.0
    }

    private fun wordNumber(value: String): Int? {
        val normalized = value.lowercase(Locale.US).replace('-', ' ').trim()
        numberWords[normalized]?.let { return it }
        val parts = normalized.split(Regex("\\s+"))
        if (parts.size == 2) {
            val tens = numberWords[parts[0]] ?: return null
            val ones = numberWords[parts[1]] ?: return null
            if (tens >= 20 && tens % 10 == 0 && ones in 1..9) return tens + ones
        }
        return null
    }

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "fifty" to 50, "sixty" to 60, "ninety" to 90
    )
}

internal fun formatCountdown(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    val secs = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}
