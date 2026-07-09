package com.seongja.jarvis

class ContextEngine {
    private val recent = ArrayDeque<String>()
    var cycles: Int = 0
        private set
    var lastIntent: IntentType = IntentType.UNKNOWN
        private set
    var lastSubject: String = "none"
        private set

    fun observe(input: String, signal: IntentSignal, entities: EntityBundle) {
        cycles++
        lastIntent = signal.type
        lastSubject = entities.targetApp ?: entities.searchQuery ?: entities.memoryPayload ?: entities.rawSubject ?: "none"
        recent.addFirst("#$cycles ${signal.type.name} -> ${lastSubject.take(48)}")
        while (recent.size > 8) recent.removeLast()
    }

    fun snapshot(): List<String> = recent.toList()

    fun continuityHint(): String = when (lastIntent) {
        IntentType.APP_OPEN -> "previous app target: $lastSubject"
        IntentType.WEB_SEARCH -> "previous search target: $lastSubject"
        IntentType.MEMORY_WRITE -> "last memory payload stored"
        else -> "no strong continuity anchor"
    }
}
