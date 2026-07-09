package com.seongja.jarvis

import android.content.Context

class JarvisBrain(context: Context) {
    private val memory = MemoryVault(context)
    private val contextEngine = ContextEngine()
    private val router = ActionRouter(context)
    private val engine = LocalBrainEngine(
        memory = memory,
        contextEngine = contextEngine,
        detector = IntentDetector(),
        extractor = EntityExtractor(),
        decisionEngine = DecisionEngine(context)
    )

    fun respond(rawInput: String): BrainResponse = engine.think(rawInput)

    fun execute(action: BrainAction): Boolean = router.execute(action)

    fun memorySnapshot(): String = memory.summary()

    fun contextSnapshot(): List<String> = engine.contextSnapshot()
}
