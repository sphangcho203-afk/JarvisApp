package com.seongja.jarvis

import java.util.Locale

class CommandRouter(
    private val actions: SystemActions,
    private val prefs: JarvisPreferences
) {
    fun handle(rawCommand: String): CommandResult {
        val clean = rawCommand.trim()
        val command = clean.lowercase(Locale.getDefault())

        if (command.isBlank()) {
            return CommandResult("I heard absolutely nothing. Stunning silence, Sir.", mode = "NO INPUT", commandCount = prefs.commandCount)
        }

        val count = prefs.nextCommandCount(clean)
        val callsign = prefs.callsign

        fun result(
            response: String,
            keepListening: Boolean = true,
            mode: String = "ONLINE",
            signal: String = "LOCAL"
        ): CommandResult {
            return CommandResult(response, keepListening, mode, signal, count)
        }

        return when {
            command.contains("stop listening") || command.contains("sleep mode") || command == "standby" -> {
                result("Voice loop paused. Tap the interface when you need me again, $callsign.", keepListening = false, mode = "STANDBY")
            }

            command.contains("help") || command.contains("what can you do") || command.contains("list commands") -> {
                result("Try: system status, battery status, open YouTube, open Spotify, search for something, set callsign to your name, clear memory, or sleep mode.", mode = "HELP")
            }

            command.contains("system status") || command.contains("status report") || command == "status" -> {
                result("Systems online. ${actions.batteryLine()} Time is ${actions.currentClockLine()}. Commands processed: $count.", mode = "DIAGNOSTIC")
            }

            command.contains("battery") || command.contains("power level") -> {
                result(actions.batteryLine(), mode = "POWER")
            }

            command.contains("what time") || command == "time" -> {
                result("It is ${actions.currentClockLine()}, $callsign.", mode = "CLOCK")
            }

            command.startsWith("set callsign to ") -> {
                val name = valueAfter(clean, "set callsign to").take(24)
                if (name.isBlank()) result("Callsign update failed. You gave me air, $callsign.", mode = "MEMORY")
                else {
                    prefs.callsign = name
                    result("Callsign updated. I will address you as $name.", mode = "MEMORY")
                }
            }

            command.startsWith("call me ") -> {
                val name = valueAfter(clean, "call me").take(24)
                if (name.isBlank()) result("Name update failed. Astonishingly, names still require letters.", mode = "MEMORY")
                else {
                    prefs.callsign = name
                    result("Understood. Callsign set to $name.", mode = "MEMORY")
                }
            }

            command.contains("who am i") || command.contains("what is my callsign") -> {
                result("You are $callsign. Local memory confirms it, unless Android eats preferences for breakfast.", mode = "MEMORY")
            }

            command.contains("clear memory") || command.contains("reset callsign") -> {
                prefs.clearSoftMemory()
                result("Soft memory cleared. Callsign reset to Sir. A tiny identity crisis, neatly contained.", mode = "MEMORY")
            }

            command.contains("open notification") -> {
                val opened = actions.openNotificationSettings()
                result(if (opened) "Opening notification settings." else "I could not open notification settings.", mode = "ACTION")
            }

            command.contains("open settings") -> {
                val opened = actions.openSettings()
                result(if (opened) "Opening Android settings." else "Settings refused to open. Bold behavior from a settings app.", mode = "ACTION")
            }

            command.startsWith("open ") -> {
                val target = valueAfter(clean, "open").lowercase(Locale.getDefault())
                val opened = actions.openKnownApp(target)
                result(
                    if (opened) "Opening $target."
                    else "I do not have a launcher route for $target yet. Known apps: ${actions.knownAppsLine()}.",
                    mode = "ACTION"
                )
            }

            command.startsWith("search for ") -> {
                val query = valueAfter(clean, "search for")
                val opened = actions.searchWeb(query)
                result(if (opened) "Searching for $query." else "Search failed because the query was empty or Android refused cooperation.", mode = "WEB", signal = "NETWORK")
            }

            command.startsWith("google ") -> {
                val query = valueAfter(clean, "google")
                val opened = actions.searchWeb(query)
                result(if (opened) "Searching Google for $query." else "Google command failed. Humanity may continue briefly.", mode = "WEB", signal = "NETWORK")
            }

            command.startsWith("go to ") || command.startsWith("open website ") -> {
                val url = if (command.startsWith("go to ")) valueAfter(clean, "go to") else valueAfter(clean, "open website")
                val opened = actions.openUrl(url)
                result(if (opened) "Opening $url." else "Could not open that website.", mode = "WEB", signal = "NETWORK")
            }

            command.contains("mission brief") || command.contains("brief me") -> {
                result("Phase 2 is active: HUD telemetry, command memory, battery diagnostics, app launching, web routing, and persistent callsign are online.", mode = "BRIEFING")
            }

            command.contains("hello") || command.contains("wake up") || command.contains("jarvis") -> {
                result("Systems online, $callsign. Phase 2 command core is awake.", mode = "AWAKE")
            }

            else -> {
                result("Command logged: $clean. I do not have a handler for that yet, but it is now in local memory.", mode = "UNMAPPED")
            }
        }
    }

    private fun valueAfter(raw: String, prefix: String): String {
        val index = raw.lowercase(Locale.getDefault()).indexOf(prefix.lowercase(Locale.getDefault()))
        return if (index >= 0) raw.substring(index + prefix.length).trim() else ""
    }
}
