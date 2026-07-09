package com.seongja.jarvis

import java.util.Locale

class InputNormalizer {
    fun clean(input: String): String = input
        .trim()
        .replace(Regex("\\s+"), " ")

    fun lower(input: String): String = clean(input).lowercase(Locale.US)

    fun tokens(input: String): List<String> = lower(input)
        .replace(Regex("[^a-z0-9 ._-]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    fun compact(input: String): String = lower(input).replace("'", "").replace(" ", "")
}
