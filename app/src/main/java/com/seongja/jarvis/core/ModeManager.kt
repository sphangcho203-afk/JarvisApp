package com.seongja.jarvis.core

import com.seongja.jarvis.models.JarvisMode

class ModeManager {
    var currentMode: JarvisMode = JarvisMode.ONLINE
        private set

    fun setMode(mode: JarvisMode): JarvisMode {
        currentMode = mode
        return currentMode
    }

    fun modeFromCommand(command: String): JarvisMode? {
        val text = command.lowercase()
        return when {
            "red alert" in text || "alert mode" in text -> JarvisMode.RED_ALERT
            "tactical" in text || "combat mode" in text -> JarvisMode.TACTICAL
            "stealth" in text -> JarvisMode.STEALTH
            "security" in text || "secure mode" in text -> JarvisMode.SECURITY
            "sleep" in text || "go dormant" in text || "standby" in text -> JarvisMode.DORMANT
            "wake" in text || "online" in text -> JarvisMode.ONLINE
            else -> null
        }
    }
}
