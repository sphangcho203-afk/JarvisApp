package com.seongja.jarvis

/**
 * Incremental output firewall for audible LLM deltas.
 *
 * Hidden reasoning tags may arrive across network chunks, so a protected tail is
 * always retained. Audible text is released only at a word or sentence boundary;
 * arbitrary token fragments are never handed to the speech provider.
 */
class StreamingResponseFilter {
    private val buffer = StringBuilder()
    private var hiddenTag: String? = null

    fun reset() {
        buffer.setLength(0)
        hiddenTag = null
    }

    fun push(chunk: String): String {
        if (chunk.isEmpty()) return ""
        buffer.append(chunk)
        return drain(final = false)
    }

    fun finish(): String = drain(final = true)

    private fun drain(final: Boolean): String {
        val output = StringBuilder()
        while (true) {
            val activeHidden = hiddenTag
            if (activeHidden != null) {
                val closing = "</$activeHidden>"
                val closingIndex = buffer.indexOfIgnoreCase(closing)
                if (closingIndex >= 0) {
                    buffer.delete(0, closingIndex + closing.length)
                    hiddenTag = null
                    continue
                }
                if (final) buffer.setLength(0) else keepBoundaryTail()
                break
            }

            val opening = earliestOpeningTag()
            if (opening != null) {
                if (opening.index > 0) {
                    output.append(emitVisiblePrefix(opening.index, final = true))
                }
                buffer.delete(0, opening.literal.length)
                hiddenTag = opening.name
                continue
            }

            if (final) {
                output.append(buffer)
                buffer.setLength(0)
            } else {
                output.append(emitVisiblePrefix(buffer.length - TAG_BOUNDARY_CHARS, final = false))
            }
            break
        }
        return output.toString()
            .replace(Regex("(?i)</?(think|analysis|reasoning|scratchpad|internal|chain_of_thought)>"), "")
    }

    private fun emitVisiblePrefix(requestedLimit: Int, final: Boolean): String {
        if (requestedLimit <= 0 || buffer.isEmpty()) return ""
        val limit = requestedLimit.coerceAtMost(buffer.length)
        val emitLength = when {
            final -> limit
            limit < MIN_PHRASE_CHARS -> 0
            else -> safeBoundary(limit)
        }
        if (emitLength <= 0) return ""
        val value = buffer.substring(0, emitLength)
        buffer.delete(0, emitLength)
        return value
    }

    /** Prefer sentence punctuation, then a normal whitespace boundary. */
    private fun safeBoundary(limit: Int): Int {
        val text = buffer.toString()
        val sentenceFloor = (limit - SENTENCE_LOOKBACK).coerceAtLeast(MIN_PHRASE_CHARS)
        for (index in limit - 1 downTo sentenceFloor) {
            if (text[index] in SENTENCE_BOUNDARIES) return index + 1
        }
        for (index in limit - 1 downTo MIN_PHRASE_CHARS) {
            if (text[index].isWhitespace()) return index + 1
        }
        return if (buffer.length >= HARD_BUFFER_LIMIT) {
            for (index in limit - 1 downTo 1) {
                if (text[index].isWhitespace()) return index + 1
            }
            limit
        } else {
            0
        }
    }

    private fun earliestOpeningTag(): OpeningTag? = HIDDEN_TAGS
        .mapNotNull { tag ->
            val literal = "<$tag>"
            val index = buffer.indexOfIgnoreCase(literal)
            if (index >= 0) OpeningTag(tag, literal, index) else null
        }
        .minByOrNull { it.index }

    private fun keepBoundaryTail() {
        if (buffer.length <= TAG_BOUNDARY_CHARS) return
        buffer.delete(0, buffer.length - TAG_BOUNDARY_CHARS)
    }

    private fun StringBuilder.indexOfIgnoreCase(value: String): Int =
        toString().indexOf(value, ignoreCase = true)

    private data class OpeningTag(
        val name: String,
        val literal: String,
        val index: Int
    )

    companion object {
        private val HIDDEN_TAGS = listOf(
            "think",
            "analysis",
            "reasoning",
            "scratchpad",
            "internal",
            "chain_of_thought"
        )
        private val SENTENCE_BOUNDARIES = setOf('.', '!', '?', ';', ':', ',', '\n')
        private const val TAG_BOUNDARY_CHARS = 48
        private const val MIN_PHRASE_CHARS = 28
        private const val SENTENCE_LOOKBACK = 96
        private const val HARD_BUFFER_LIMIT = 180
    }
}
