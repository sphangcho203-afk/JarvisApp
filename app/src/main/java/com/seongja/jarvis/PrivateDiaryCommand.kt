package com.seongja.jarvis

import java.util.Locale

sealed class PrivateDiaryCommand {
    data object Open : PrivateDiaryCommand()
    data object NewEntry : PrivateDiaryCommand()
    data object SecureVault : PrivateDiaryCommand()
    data class Search(val query: String) : PrivateDiaryCommand()
}

object PrivateDiaryCommandParser {
    private val diaryNouns = Regex(
        "\\b(private diary|my diary|diary|journal|private notes?|secure notes?|notes vault|diary vault)\\b",
        RegexOption.IGNORE_CASE
    )

    private val relativeTime = Regex(
        "\\b(today|yesterday|this week|last week|this month|last month|last (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): PrivateDiaryCommand? {
        val clean = normalize(raw)
        if (clean.isBlank()) return null

        if (
            clean.contains("secure the vault") ||
            clean.contains("lock the diary") ||
            clean.contains("close my private diary") ||
            clean.contains("seal the diary")
        ) {
            return PrivateDiaryCommand.SecureVault
        }

        if (
            Regex("\\b(what|show|find|search|read|look)\\b").containsMatchIn(clean) &&
            Regex("\\b(i wrote|i recorded|my entries|my notes|diary|journal)\\b").containsMatchIn(clean)
        ) {
            val period = relativeTime.find(clean)?.value
            if (!period.isNullOrBlank()) return PrivateDiaryCommand.Search(period)

            val topic = Regex(
                "(?:about|for|containing|on)\\s+(.+)",
                RegexOption.IGNORE_CASE
            ).find(clean)?.groupValues?.getOrNull(1)?.trim()
            if (!topic.isNullOrBlank()) return PrivateDiaryCommand.Search(topic.take(240))
        }

        if (!diaryNouns.containsMatchIn(clean)) return null

        if (
            Regex("\\b(new|create|write|add|record|start)\\b").containsMatchIn(clean) &&
            Regex("\\b(entry|note|page|diary|journal)\\b").containsMatchIn(clean)
        ) {
            return PrivateDiaryCommand.NewEntry
        }

        val searchMatch = Regex(
            "(?:find|search|show|look for|read)\\s+(?:my\\s+)?(?:private\\s+)?(?:diary(?:\\s+entries?)?|journal(?:\\s+entries?)?|notes?|entries)\\s+(?:for|about|containing|on)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (searchMatch != null) {
            return PrivateDiaryCommand.Search(searchMatch.groupValues[1].trim().take(240))
        }

        relativeTime.find(clean)?.value?.let { period ->
            if (Regex("\\b(show|find|search|read|what)\\b").containsMatchIn(clean)) {
                return PrivateDiaryCommand.Search(period)
            }
        }

        if (
            Regex("\\b(open|show|launch|enter|unlock|bring)\\b").containsMatchIn(clean) ||
            clean == "my private diary" ||
            clean == "private diary"
        ) {
            return PrivateDiaryCommand.Open
        }

        return PrivateDiaryCommand.Open
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
