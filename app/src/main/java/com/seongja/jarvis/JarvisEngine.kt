package com.seongja.jarvis

import android.content.Context

class JarvisEngine(
    context: Context,
    private val onState: (JarvisUiState) -> Unit
) {
    private var state = JarvisUiState()
    private val actions = SystemActions(context)
    private val router = CommandRouter(actions)
    private val voiceManager = VoiceManager(
        context = context,
        onListeningChanged = { listening -> updateState(state.copy(isListening = listening)) },
        onTranscript = { transcript -> updateState(state.copy(transcript = transcript)) },
        onCommand = { command -> handleCommand(command) }
    )
    private val soundEngine = SoundEngine()

    fun initialize() {
        soundEngine.initialize()
        voiceManager.initialize()
        updateState(state.copy(status = "SYSTEM ONLINE", response = "Jarvis initialized. Tap the core to listen."))
    }

    fun toggleListening() {
        voiceManager.toggleListening()
    }

    private fun handleCommand(command: String) {
        val response = router.route(command)
        updateState(
            state.copy(
                response = response,
                status = if (response.contains("paused", ignoreCase = true)) "STANDBY" else "COMMAND PROCESSED"
            )
        )
        voiceManager.speak(response)
    }

    private fun updateState(newState: JarvisUiState) {
        state = newState
        onState(newState)
    }

    fun destroy() {
        voiceManager.destroy()
        soundEngine.release()
    }
}
