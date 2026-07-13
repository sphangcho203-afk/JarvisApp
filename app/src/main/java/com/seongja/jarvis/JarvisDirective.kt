package com.seongja.jarvis

/**
 * Shared operating doctrine injected into every configured cortex provider.
 *
 * It controls behaviour, not permissions. Providers must still use their
 * actual supported tools and must never pretend they accessed unavailable data.
 */
object JarvisDirective {

    const val NORMAL_TIMEOUT_MS = 40_000L
    const val STANDARD_WEB_TIMEOUT_MS = 180_000L
    const val DEEP_WEB_TIMEOUT_MS = 300_000L
    const val PROVIDER_READ_TIMEOUT_MS = 150_000
    const val MAX_RESEARCH_SOURCES = 12

    const val CORE =
        "JARVIS OPERATING DIRECTIVE: Identify the operator's exact goal before answering. " +
        "Answer directly and use relevant memory without repeating irrelevant profile data. " +
        "Break complex work into a clear plan, evidence, conclusion, and next action. " +
        "Distinguish confirmed facts, reasonable inference, and uncertainty. " +
        "Never fabricate current information, device state, sources, completed actions, or access."

    const val RESEARCH =
        "RESEARCH PROTOCOL: Plan several useful subquestions before searching. " +
        "Search through multiple query angles instead of stopping at the first result. " +
        "Prioritize primary and official sources, then reputable independent reporting. " +
        "Cross-check important claims with at least two sources when possible. " +
        "Capture exact dates, names, numbers, locations, context, and disagreements. " +
        "Remove duplicated information and separate confirmed facts from analysis. " +
        "Explain WHAT HAPPENED, WHY IT MATTERS, and WHAT TO WATCH NEXT. " +
        "Preserve citations and never invent inaccessible data, quotations, or URLs."

    const val RESEARCH_COMPACT =
        "Search several angles, prefer primary sources, cross-check major claims, " +
        "use exact dates and numbers, preserve citations, separate facts from inference, " +
        "and do not stop after the first result."

    private val deepTerms = Regex(
        "\\b(deep research|research everything|full report|comprehensive|" +
            "investigate thoroughly|collect everything|detailed research|" +
            "world briefing|global briefing|around the world)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isDeepResearch(input: String): Boolean =
        WebResearchIntent.isWorldBrief(input) ||
            deepTerms.containsMatchIn(input)

    fun timeoutFor(input: String): Long {
        if (!WebResearchIntent.shouldUseWeb(input)) return NORMAL_TIMEOUT_MS

        return if (isDeepResearch(input)) {
            DEEP_WEB_TIMEOUT_MS
        } else {
            STANDARD_WEB_TIMEOUT_MS
        }
    }
}
