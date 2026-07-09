package com.seongja.jarvis

class LocalBrainEngine(
    private val memory: MemoryVault,
    private val contextEngine: ContextEngine,
    private val detector: IntentDetector,
    private val extractor: EntityExtractor,
    private val decisionEngine: DecisionEngine
) {
    fun think(input: String): BrainResponse {
        val signal = detector.detect(input)
        val entities = extractor.extract(input, signal)
        contextEngine.observe(input, signal, entities)
        return decisionEngine.decide(input, signal, entities, memory, contextEngine)
    }

    fun contextSnapshot(): List<String> = contextEngine.snapshot()
}
