package com.seongja.jarvis

/**
 * Incremental output firewall for audible LLM deltas.
 *
 * It preserves token spacing while removing hidden reasoning blocks even when
 * their tags are split across multiple network chunks.
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
                if (final) buffer.setLength(0)
                else keepBoundaryTail()
                break
            }

            val opening = earliestOpeningTag()
            if (opening != null) {
                if (opening.index > 0) output.append(buffer.substring(0, opening.index))
                buffer.delete(0, opening.index + opening.literal.length)
                hiddenTag = opening.name
                continue
            }

            if (final) {
                output.append(buffer)
                buffer.setLength(0)
            } else if (buffer.length > TAG_BOUNDARY_CHARS) {
                val emitLength = buffer.length - TAG_BOUNDARY_CHARS
                output.append(buffer.substring(0, emitLength))
                buffer.delete(0, emitLength)
            }
            break
        }
        return output.toString()
            .replace(Regex("(?i)</?(think|analysis|reasoning|scratchpad|internal|chain_of_thought)>"), "")
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
        private const val TAG_BOUNDARY_CHARS = 40
    }
}
