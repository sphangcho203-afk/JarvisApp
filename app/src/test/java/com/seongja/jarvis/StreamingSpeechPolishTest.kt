package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSpeechPolishTest {

    @Test
    fun streamingFilterNeverEmitsAnIntermediateWordFragment() {
        val filter = StreamingResponseFilter()
        val source = "Weather intelligence is monitoring nearby rainfall while the network route remains stable and the response terminal stays readable."
        val emitted = mutableListOf<String>()

        source.forEach { character ->
            filter.push(character.toString()).takeIf(String::isNotEmpty)?.let(emitted::add)
        }
        filter.finish().takeIf(String::isNotEmpty)?.let(emitted::add)

        assertTrue(emitted.size >= 2)
        emitted.dropLast(1).forEach { chunk ->
            val last = chunk.last()
            assertTrue(
                "Intermediate speech chunk ended inside a word: $chunk",
                last.isWhitespace() || last in setOf('.', '!', '?', ';', ':', ',', '\n')
            )
        }
        assertTrue(emitted.joinToString("") == source)
    }

    @Test
    fun hiddenReasoningIsNeverReleasedToSpeech() {
        val filter = StreamingResponseFilter()
        val audible = buildString {
            append(filter.push("The verified answer is ready. <thi"))
            append(filter.push("nk>private scratchpad text</think> Continue naturally."))
            append(filter.finish())
        }

        assertFalse(audible.contains("scratchpad", ignoreCase = true))
        assertTrue(audible.contains("The verified answer is ready."))
        assertTrue(audible.contains("Continue naturally."))
    }

    @Test
    fun spokenOutputRepairsDottedAndLetterSpacedWords() {
        val spoken = JarvisResponseSanitizer.spoken(
            "F.R.I.D.A.Y. says H E L L O. WEATHER CORE // WHATSAPP READY."
        )

        assertTrue(spoken.contains("Friday"))
        assertTrue(spoken.contains("Hello"))
        assertTrue(spoken.contains("weather", ignoreCase = true))
        assertTrue(spoken.contains("WhatsApp"))
        assertFalse(spoken.contains("F.R.I.D.A.Y."))
    }

    @Test
    fun visibleResponseRemovesPlatformEmoji() {
        val visible = JarvisResponseSanitizer.clean(
            "Weather alert active. ⚠ Nearby rain ☂ with a provider emoji 😀."
        )

        assertTrue(visible.contains("Weather alert active."))
        assertFalse(visible.contains("⚠"))
        assertFalse(visible.contains("☂"))
        assertFalse(visible.contains("😀"))
    }
}
