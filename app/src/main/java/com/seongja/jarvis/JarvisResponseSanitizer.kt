package com.seongja.jarvis

/**
 * Final-output firewall for provider responses.
 *
 * Reasoning providers occasionally emit private scratchpad tags despite prompt
 * instructions. Nothing inside those blocks belongs in the HUD, speech output,
 * memory, or conversation history. Legacy identity words are normalized here so
 * every visible and audible answer belongs to FRIDAY, not an earlier build name.
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
            .replace(Regex("(?i)\\bJarvis\\b"), "FRIDAY")
            .replace(Regex("(?i)\\bSir\\b"), "Boss")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()

        return value.ifBlank {
            "The provider returned private reasoning without a usable final answer. Retry the request, Boss."
        }
    }

    fun spoken(raw: String): String {
        var value = clean(raw)
            .replace(Regex("```[\\s\\S]*?```"), " Code is displayed on screen. ")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\[(\\d+)]"), "")
            .replace(Regex("[*_#>`]"), " ")
            .replace(Regex("\\s*//+\\s*"), ". ")
            .replace(Regex("\\s*(?:->|→)\\s*"), ". ")
            .replace(DECORATIVE_UNICODE, " ")

        value = DOTTED_OR_SPACED_WORD.replace(value) { match ->
            val joined = match.value.filter(Char::isLetter)
            naturalWord(joined)
        }

        value = ALL_CAPS_WORD.replace(value) { match ->
            naturalWord(match.value)
        }

        return value
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("([.!?]){2,}"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun naturalWord(raw: String): String {
        val upper = raw.uppercase()
        return when (upper) {
            "FRIDAY" -> "Friday"
            "GMAIL" -> "Gmail"
            "WHATSAPP" -> "WhatsApp"
            "CARTESIA" -> "Cartesia"
            "DEEPSEEK" -> "DeepSeek"
            "GEMINI" -> "Gemini"
            "YOUTUBE" -> "YouTube"
            "HELIX" -> "Helix"
            "ANDROID" -> "Android"
            "WEATHER" -> "weather"
            "RESPONSE" -> "response"
            "SYSTEM" -> "system"
            "NETWORK" -> "network"
            "PRIVATE" -> "private"
            "CORTEX" -> "cortex"
            "HTTP", "HTTPS", "API", "APK", "GPS", "CPU", "GPU", "PCM", "JSON", "HTML", "CSS", "URL", "SMS" -> upper
            else -> raw.lowercase().replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }
    }

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
    private val DOTTED_OR_SPACED_WORD = Regex(
        "(?i)(?<![A-Za-z])(?:[A-Za-z][.\\s-]){3,}[A-Za-z](?![A-Za-z])"
    )
    private val ALL_CAPS_WORD = Regex("\\b[A-Z]{4,}\\b")
    private val DECORATIVE_UNICODE = Regex("[\\u2600-\\u27BF\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")
}
