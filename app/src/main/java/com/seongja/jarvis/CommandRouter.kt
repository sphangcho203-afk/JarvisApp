package com.seongja.jarvis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandRouter(private val actions: SystemActions) {

    fun route(rawCommand: String): String {
        val command = rawCommand.trim().lowercase(Locale.getDefault())

        if (command.isBlank()) return "I heard silence. Very advanced command, Sir."

        return when {
            command.contains("wake up") || command.contains("jarvis") -> {
                "Online and listening, Sir."
            }

            command.contains("system status") || command.contains("status") -> {
                "All primary systems are online. Voice core, HUD, and command router are active."
            }

            command.contains("time") -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                "Current time is $time."
            }

            command.contains("open settings") -> {
                if (actions.openSettings()) "Opening settings." else "I could not open settings."
            }

            command.contains("open youtube") || command.contains("open you tube") -> {
                if (actions.openYouTube()) "Opening YouTube." else "I could not open YouTube."
            }

            command.contains("open chrome") -> {
                if (actions.openChrome()) "Opening Chrome." else "I could not open Chrome."
            }

            command.startsWith("search for ") -> {
                val query = rawCommand.substringAfter("search for", "").trim()
                if (query.isNotBlank() && actions.searchWeb(query)) {
                    "Searching for $query."
                } else {
                    "Search query missing. Even machines need nouns, Sir."
                }
            }

            command.contains("stop listening") -> {
                "Voice loop paused."
            }

            else -> {
                "Command received: $rawCommand. No action module is assigned yet."
            }
        }
    }
}
