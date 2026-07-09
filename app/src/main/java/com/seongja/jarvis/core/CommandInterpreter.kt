package com.seongja.jarvis.core

class CommandInterpreter {
    fun normalize(input: String): String = input.trim().lowercase().replace(Regex("\\s+"), " ")

    fun extractAfterAny(input: String, markers: List<String>): String? {
        val normalized = normalize(input)
        markers.forEach { marker ->
            val index = normalized.indexOf(marker)
            if (index >= 0) {
                val value = input.substring((index + marker.length).coerceAtMost(input.length)).trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }
}
