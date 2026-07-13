package com.seongja.jarvis

import java.util.Locale

/**
 * Converts raw Android ASR text into two forms:
 *
 * 1. commandText: stable text for routing and language models.
 * 2. displayText: readable sentence casing and punctuation for the HUD.
 *
 * Android speech recognition commonly returns words without punctuation. This
 * layer restores safe sentence punctuation and supports explicit spoken symbol
 * names during dictation without corrupting ordinary phrases such as
 * "the period of a wave".
 */
object SpeechCommandNormalizer {

    data class Result(
        val rawText: String,
        val commandText: String,
        val displayText: String,
        val explicitDictation: Boolean
    )

    private val dictationPrefix = Regex(
        "^(?:type|write|dictate|enter|insert|spell|say exactly|transcribe)\\s+",
        RegexOption.IGNORE_CASE
    )

    private val questionPrefixes = listOf(
        "what ", "whats ", "what's ", "why ", "how ", "when ", "where ",
        "who ", "which ", "can ", "could ", "would ", "will ", "do ",
        "does ", "did ", "is ", "are ", "am ", "should ", "may ",
        "tell me whether ", "explain whether "
    )

    private val jarvisVariants = Regex(
        "\\b(?:jarvie|jarvy|jarvis|javis|jarves|jarviss)\\b",
        RegexOption.IGNORE_CASE
    )

    fun normalize(raw: String): Result {
        val compact = raw
            .replace(Regex("\\s+"), " ")
            .trim()
        if (compact.isBlank()) return Result(raw, "", "", false)

        val canonicalNames = compact.replace(jarvisVariants, "Jarvis")
        val explicit = dictationPrefix.containsMatchIn(canonicalNames)
        val symbolExpanded = if (explicit) expandSpokenSymbols(canonicalNames) else canonicalNames
        val command = normalizeSpacing(symbolExpanded)
        val display = punctuate(command, explicit)

        return Result(
            rawText = raw,
            commandText = command,
            displayText = display,
            explicitDictation = explicit
        )
    }

    fun formatPartial(raw: String): String {
        val compact = raw.replace(Regex("\\s+"), " ").trim()
        if (compact.isBlank()) return ""
        return sentenceCase(compact.replace(jarvisVariants, "Jarvis"))
    }

    private fun expandSpokenSymbols(value: String): String {
        var output = value
        val replacements = listOf(
            "new paragraph" to "\n\n",
            "new line" to "\n",
            "open curly bracket" to "{",
            "close curly bracket" to "}",
            "open brace" to "{",
            "close brace" to "}",
            "open square bracket" to "[",
            "close square bracket" to "]",
            "open bracket" to "(",
            "close bracket" to ")",
            "question mark" to "?",
            "exclamation mark" to "!",
            "exclamation point" to "!",
            "full stop" to ".",
            "period" to ".",
            "semicolon" to ";",
            "semi colon" to ";",
            "colon" to ":",
            "comma" to ",",
            "percent sign" to "%",
            "percentage sign" to "%",
            "multiply sign" to "×",
            "multiplication sign" to "×",
            "times sign" to "×",
            "euro sign" to "€",
            "dollar sign" to "${'$'}",
            "cent sign" to "¢",
            "equals sign" to "=",
            "equal sign" to "=",
            "delta sign" to "∆",
            "section sign" to "§",
            "back tick" to "`",
            "backtick" to "`",
            "at sign" to "@",
            "hash sign" to "#",
            "hashtag" to "#",
            "ampersand" to "&",
            "underscore" to "_",
            "forward slash" to "/",
            "back slash" to "\\",
            "plus sign" to "+",
            "minus sign" to "−"
        )

        replacements.sortedByDescending { it.first.length }.forEach { (spoken, symbol) ->
            val pattern = Regex("\\b${Regex.escape(spoken)}\\b", RegexOption.IGNORE_CASE)
            output = pattern.replace(output) { symbol }
        }
        return output
    }

    private fun normalizeSpacing(value: String): String = value
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\s+([,.;:!?%)}\\]])"), "\$1")
        .replace(Regex("([({\\[])\\s+"), "\$1")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun punctuate(value: String, explicitDictation: Boolean): String {
        if (value.isBlank()) return value
        var result = improveReadableGrammar(value)
        result = sentenceCase(result)
        if (explicitDictation) return result

        val lower = result.lowercase(Locale.getDefault()).trim()
        val alreadyPunctuated = result.lastOrNull() in setOf('.', '?', '!', ':', ';')
        if (!alreadyPunctuated) {
            result += if (questionPrefixes.any(lower::startsWith)) "?" else "."
        }
        return result
    }

    private fun improveReadableGrammar(value: String): String {
        var result = value
        val contractions = listOf(
            Regex("\\bwhats\\b", RegexOption.IGNORE_CASE) to "what's",
            Regex("\\bdont\\b", RegexOption.IGNORE_CASE) to "don't",
            Regex("\\bcant\\b", RegexOption.IGNORE_CASE) to "can't",
            Regex("\\bwont\\b", RegexOption.IGNORE_CASE) to "won't",
            Regex("\\bisnt\\b", RegexOption.IGNORE_CASE) to "isn't",
            Regex("\\barent\\b", RegexOption.IGNORE_CASE) to "aren't",
            Regex("\\bim\\b", RegexOption.IGNORE_CASE) to "I'm",
            Regex("\\bive\\b", RegexOption.IGNORE_CASE) to "I've",
            Regex("\\byoure\\b", RegexOption.IGNORE_CASE) to "you're",
            Regex("\\btheyre\\b", RegexOption.IGNORE_CASE) to "they're"
        )
        contractions.forEach { (pattern, replacement) ->
            result = pattern.replace(result, replacement)
        }

        result = result
            .replace(Regex("^Jarvis\\s+", RegexOption.IGNORE_CASE), "Jarvis, ")
            .replace(Regex("^(Hey|Hello|Hi)\\s+Jarvis$", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}, Jarvis"
            }
            .replace(Regex("^(Good morning|Good afternoon|Good evening)\\s+Jarvis$", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}, Jarvis"
            }
            .replace(Regex("\\s+Jarvis$", RegexOption.IGNORE_CASE), ", Jarvis")
            .replace(
                Regex("^(Well|Actually|Basically|However|Therefore|Meanwhile|Please)\\s+", RegexOption.IGNORE_CASE)
            ) { "${it.groupValues[1]}, " }

        return normalizeSpacing(result)
    }

    private fun sentenceCase(value: String): String {
        if (value.isBlank()) return value
        val chars = value.toCharArray()
        var capitalizeNext = true
        for (index in chars.indices) {
            val char = chars[index]
            if (capitalizeNext && char.isLetter()) {
                chars[index] = char.uppercaseChar()
                capitalizeNext = false
            }
            if (char == '.' || char == '?' || char == '!' || char == '\n') {
                capitalizeNext = true
            }
        }
        return String(chars)
    }
}
