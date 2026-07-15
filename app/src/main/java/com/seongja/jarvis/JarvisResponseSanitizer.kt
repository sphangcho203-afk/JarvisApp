package com.seongja.jarvis

/**
 * Final-output firewall for provider responses.
 *
 * Reasoning providers occasionally emit private scratchpad tags despite prompt
 * instructions. Nothing inside those blocks belongs in the HUD, speech output,
 * memory, or conversation history.
 */
object JarvisResponseSanitizer {

    fun clean(raw: String, groundedResearch: Boolean = false): String {
        var value = raw
            .replace(HIDDEN_XML_BLOCK, " ")
            .replace(HIDDEN_FENCE_BLOCK, " ")
            .replace(HIDDEN_TAG, " ")
            .replace(LEADING_FINAL_LABEL, "")
            .trim()

        if (groundedResearch) {
            value = value
                .replace(KNOWLEDGE_CUTOFF_SENTENCE, " ")
                .replace(AVAILABLE_EVIDENCE_PREFACE, "")
                .trim()
        }

        value = value
            .replace(Regex("(?i)\\bthe user's\\b"), "your")
            .replace(Regex("(?i)\\bthe user is\\b"), "you are")
            .replace(Regex("(?i)\\bthe user has\\b"), "you have")
            .replace(Regex("(?i)\\bthe user wants\\b"), "you want")
            .replace(Regex("(?i)\\bthe user asked\\b"), "you asked")
            .replace(Regex("(?i)\\bthe user\\b"), "you")
            .replace(Regex("(?i)\\bthe operator's\\b"), "your")
            .replace(Regex("(?i)\\bthe operator\\b"), "you")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()

        return value.ifBlank {
            "The provider returned private reasoning without a usable final answer. Retry the request, Boss."
        }
    }

    fun spoken(raw: String): String = clean(raw)
        .replace(Regex("(?i)\\bSir\\b"), "Boss")
        .replace(Regex("(?i)\\bJarvis\\b"), "FRIDAY")
        .replace(Regex("```[\\s\\S]*?```"), " Code is displayed on screen. ")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\[(\\d+)]"), "")
        .replace(Regex("[*_#>`]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val HIDDEN_XML_BLOCK = Regex(
        "(?is)<(think|analysis|reasoning|scratchpad|internal|chain_of_thought)>.*?</\\1>"
    )
    private val HIDDEN_FENCE_BLOCK = Regex(
        "(?is)```(?:think|analysis|reasoning|scratchpad|internal)[\\s\\S]*?```"
    )
    private val HIDDEN_TAG = Regex(
        "(?i)</?(think|analysis|reasoning|scratchpad|internal|chain_of_thought)>"
    )
    private val LEADING_FINAL_LABEL = Regex(
        "(?i)^\\s*(final answer|answer|response)\\s*:\\s*"
    )
    private val KNOWLEDGE_CUTOFF_SENTENCE = Regex(
        "(?is)(?:however,?\\s*)?(?:please note that\\s*)?[^.!?]{0,120}knowledge cutoff[^.!?]*[.!?]"
    )
    private val AVAILABLE_EVIDENCE_PREFACE = Regex(
        "(?is)^\\s*(?:from|based on) the available evidence,?\\s*"
    )
}
