package com.seongja.jarvis

class IntentDetector(private val normalizer: InputNormalizer = InputNormalizer()) {
    fun detect(input: String): IntentSignal {
        val lower = normalizer.lower(input)
        val compact = normalizer.compact(input)
        val trace = mutableListOf("normalize", "token_scan", "pattern_matrix")

        fun signal(type: IntentType, confidence: Float, matched: String, extra: String): IntentSignal {
            trace += extra
            return IntentSignal(type, confidence.coerceIn(0f, 1f), matched, trace)
        }

        if (compact.contains("whoami") || compact.contains("whoiam") || compact.contains("whomai") || lower.contains("who am i") || lower.contains("who i am")) {
            return signal(IntentType.IDENTITY_QUERY, 0.99f, "identity phrase", "identity_vector")
        }

        if (lower.startsWith("call me ") || lower.startsWith("my name is ") || lower.startsWith("i am ") || lower.startsWith("i'm ") || lower.startsWith("im ")) {
            return signal(IntentType.MEMORY_WRITE, 0.96f, "identity memory write", "memory_identity")
        }

        if (lower.startsWith("remember ") || lower.startsWith("remember that ") || lower.startsWith("store ") || lower.startsWith("save this")) {
            return signal(IntentType.MEMORY_WRITE, 0.95f, "memory write", "memory_payload")
        }

        if (lower.contains("what do you remember") || lower.contains("show memory") || lower.contains("memory vault") || lower.contains("open memory")) {
            return signal(IntentType.MEMORY_READ, 0.96f, "memory read", "memory_recall")
        }

        if (lower.contains("clear memory") || lower.contains("wipe memory") || lower.contains("delete memory")) {
            return signal(IntentType.MEMORY_CLEAR, 0.98f, "memory wipe", "security_memory_clear")
        }

        if (lower.contains("open settings") || lower.contains("phone settings")) {
            return signal(IntentType.APP_OPEN, 0.94f, "open settings", "system_action")
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") || lower.contains("open youtube") || lower.contains("open chrome")) {
            return signal(IntentType.APP_OPEN, 0.88f, "app open", "app_target")
        }

        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.contains("search for ") || lower.contains("look up ")) {
            return signal(IntentType.WEB_SEARCH, 0.9f, "web search", "search_target")
        }

        if (lower.contains("tactical") || lower.contains("stealth") || lower.contains("red alert") || lower.contains("alert mode") || lower.contains("online mode") || lower.contains("normal mode")) {
            return signal(IntentType.MODE_CHANGE, 0.92f, "mode change", "mode_router")
        }

        if (lower.contains("battery") || lower.contains("system status") || lower.contains("diagnostic") || lower.contains("device status") || lower.contains("status report")) {
            return signal(IntentType.DIAGNOSTICS, 0.92f, "diagnostics", "device_probe")
        }

        if (lower.contains("how do you work") || lower.contains("explain your brain") || lower.contains("what is your brain") || lower.contains("your architecture")) {
            return signal(IntentType.SELF_EXPLAIN, 0.93f, "self explanation", "brain_introspection")
        }

        if (looksLikeKnowledge(lower)) {
            return signal(IntentType.KNOWLEDGE_QUERY, 0.72f, "knowledge query", "offline_reasoning")
        }

        if (lower.isNotBlank()) {
            return signal(IntentType.CONVERSATION, 0.62f, "general language", "conversation_router")
        }

        return signal(IntentType.UNKNOWN, 0.25f, "empty or unknown", "fallback")
    }

    private fun looksLikeKnowledge(lower: String): Boolean {
        val starters = listOf("what", "why", "how", "when", "where", "who", "explain", "tell me", "define", "analyze")
        return lower.endsWith("?") || starters.any { lower.startsWith(it) }
    }
}
