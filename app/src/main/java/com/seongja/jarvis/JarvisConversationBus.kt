package com.seongja.jarvis

import android.content.Context

/**
 * Process-wide journal used by voice input, BrainResponse, and deterministic
 * Android actions. It stores turns locally and lets MemoryVault extract stable
 * owner facts without modifying model weights or sending the full database to
 * a cloud provider.
 */
object JarvisConversationBus {
    @Volatile
    private var memory: MemoryVault? = null

    fun initialize(context: Context) {
        if (memory != null) return
        synchronized(this) {
            if (memory == null) memory = MemoryVault(context.applicationContext)
        }
    }

    fun recordUser(text: String) {
        val vault = memory ?: return
        vault.addConversation("user", text)
        vault.learnFromUserTurn(text)
    }

    fun recordAssistant(text: String) {
        memory?.addConversation("assistant", text)
    }

    fun recordSystem(text: String) {
        memory?.addConversation("system", text)
    }
}
