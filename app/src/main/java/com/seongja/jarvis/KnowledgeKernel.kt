package com.seongja.jarvis

class KnowledgeKernel {
    fun answerOffline(input: String, memory: MemoryVault): String {
        val lower = input.lowercase()
        return when {
            lower.contains("brain") || lower.contains("work") || lower.contains("architecture") -> explainBrain()
            lower.contains("jarvis") && (lower.contains("what") || lower.contains("who")) ->
                "Jarvis is your phone-first assistant project: a local brain, sci-fi HUD, voice loop, memory vault, and action router. Phase 5 focuses on local cognition before cloud intelligence."
            lower.contains("who am i") || lower.contains("who i am") -> identity(memory)
            else -> "Offline reasoning: I can parse intent, extract entities, use memory, choose actions, and explain local context. For live facts or deep knowledge, the future cloud brain will handle retrieval and model reasoning."
        }
    }

    fun identity(memory: MemoryVault): String {
        return "You are ${memory.callsign()}, my operator and creator. ${memory.profile()}"
    }

    fun explainBrain(): String {
        return "Phase 5 brain stack: input normalizer, intent detector, entity extractor, memory vault, context engine, decision engine, action router, HUD response renderer. Cloud intelligence is a future outer cortex, not the local core."
    }
}
