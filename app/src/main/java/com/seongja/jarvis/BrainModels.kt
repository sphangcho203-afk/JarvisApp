package com.seongja.jarvis

enum class BrainMode {
    BOOT,
    ONLINE,
    LISTENING,
    THINKING,
    EXECUTING,
    TACTICAL,
    STEALTH,
    SECURITY,
    ALERT,
    LEARNING
}

enum class IntentType {
    IDENTITY_QUERY,
    MEMORY_WRITE,
    MEMORY_READ,
    MEMORY_CLEAR,
    APP_OPEN,
    WEB_SEARCH,
    MODE_CHANGE,
    DIAGNOSTICS,
    SELF_EXPLAIN,
    DEVICE_UNLOCK_REQUEST,
    KNOWLEDGE_QUERY,
    CONVERSATION,
    UNKNOWN
}

enum class ActionType {
    NONE,
    OPEN_APP,
    OPEN_URL,
    WEB_SEARCH,
    OPEN_SETTINGS,
    REQUEST_DEVICE_UNLOCK
}

data class BrainAction(
    val type: ActionType = ActionType.NONE,
    val payload: String = "",
    val label: String = "none",
    val requestId: String = "",
    val requiresConfirmation: Boolean = false
)

data class IntentSignal(
    val type: IntentType,
    val confidence: Float,
    val matched: String,
    val trace: List<String>
)

data class EntityBundle(
    val targetApp: String? = null,
    val memoryPayload: String? = null,
    val identityName: String? = null,
    val searchQuery: String? = null,
    val modeTarget: BrainMode? = null,
    val rawSubject: String? = null
)

data class BrainResponse(
    val spoken: String,
    val display: String,
    val intent: String,
    val confidence: Float,
    val mode: BrainMode,
    val trace: List<String>,
    val memory: String,
    val thoughts: List<String>,
    val entities: List<String>,
    val decision: String,
    val action: BrainAction = BrainAction()
) {
    init {
        JarvisConversationBus.recordAssistant(spoken)
    }
}
