package com.seongja.jarvis.core

import com.seongja.jarvis.memory.MemoryManager
import com.seongja.jarvis.models.CommandResult
import com.seongja.jarvis.models.JarvisMode
import com.seongja.jarvis.system.AppLaunchManager
import com.seongja.jarvis.system.DeviceStatusManager
import com.seongja.jarvis.system.DiagnosticsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisEngine(
    private val memory: MemoryManager,
    private val appLaunchManager: AppLaunchManager,
    private val deviceStatusManager: DeviceStatusManager,
    private val diagnosticsManager: DiagnosticsManager,
    private val modeManager: ModeManager = ModeManager(),
    private val interpreter: CommandInterpreter = CommandInterpreter()
) {
    var commandCount: Int = 0
        private set

    fun handle(rawCommand: String): CommandResult {
        val command = interpreter.normalize(rawCommand)
        if (command.isBlank()) return CommandResult("I did not receive a command.", JarvisMode.LISTENING, executed = false)
        commandCount += 1

        modeManager.modeFromCommand(command)?.let { mode ->
            modeManager.setMode(mode)
            return CommandResult("${mode.label} mode engaged.", mode = mode)
        }

        interpreter.extractAfterAny(rawCommand, listOf("remember that", "remember", "store"))?.let { payload ->
            val parts = payload.split(" is ", limit = 2)
            return if (parts.size == 2) {
                memory.remember(parts[0], parts[1])
                CommandResult("Memory stored: ${parts[0].trim()}.", JarvisMode.EXECUTING)
            } else {
                memory.remember("note_${System.currentTimeMillis()}", payload)
                CommandResult("I stored that note locally.", JarvisMode.EXECUTING)
            }
        }

        interpreter.extractAfterAny(rawCommand, listOf("set callsign to", "call me", "my name is"))?.let { name ->
            memory.remember("callsign", name)
            return CommandResult("Confirmed. I will address you as $name.", JarvisMode.EXECUTING)
        }

        if ("who am i" in command || "what is my name" in command) {
            return CommandResult("You are ${memory.callsign()}, command authority for this Jarvis core.", JarvisMode.ONLINE)
        }

        if ("what do you remember" in command || "show memory" in command || "memory vault" in command) {
            val all = memory.allMemory()
            val reply = if (all.isEmpty()) "Memory vault is empty." else all.entries.joinToString(". ") { "${it.key}: ${it.value}" }
            return CommandResult(reply, JarvisMode.SECURITY)
        }

        if ("clear memory" in command || "wipe memory" in command) {
            memory.clearAll()
            return CommandResult("Local memory vault cleared.", JarvisMode.SECURITY, visualAlert = true)
        }

        if ("battery" in command) {
            return CommandResult(deviceStatusManager.batteryReport(), JarvisMode.ONLINE)
        }

        if ("diagnostic" in command || "system status" in command || "device status" in command) {
            val telemetry = deviceStatusManager.telemetry(commandCount, micReady = true, permissionStatus = "GRANTED")
            return CommandResult(diagnosticsManager.longReport(telemetry), JarvisMode.TACTICAL)
        }

        if ("what time" in command || command == "time") {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return CommandResult("The time is $time.", JarvisMode.ONLINE)
        }

        if ("open settings" in command) {
            val ok = appLaunchManager.openSettings()
            return CommandResult(if (ok) "Opening settings." else "Unable to open settings.", JarvisMode.EXECUTING, ok)
        }

        if ("wifi settings" in command || "wi-fi settings" in command) {
            val ok = appLaunchManager.openWifiSettings()
            return CommandResult(if (ok) "Opening Wi-Fi settings." else "Unable to open Wi-Fi settings.", JarvisMode.EXECUTING, ok)
        }

        if ("bluetooth" in command) {
            val ok = appLaunchManager.openBluetoothSettings()
            return CommandResult(if (ok) "Opening Bluetooth settings." else "Unable to open Bluetooth settings.", JarvisMode.EXECUTING, ok)
        }

        if ("battery saver" in command) {
            val ok = appLaunchManager.openBatterySettings()
            return CommandResult(if (ok) "Opening battery settings." else "Unable to open battery settings.", JarvisMode.EXECUTING, ok)
        }

        if (command.startsWith("open ")) {
            val target = command.removePrefix("open ").trim()
            val ok = appLaunchManager.openKnownApp(target) || appLaunchManager.openUrl(target)
            return CommandResult(if (ok) "Opening $target." else "I could not open $target.", JarvisMode.EXECUTING, ok)
        }

        interpreter.extractAfterAny(rawCommand, listOf("search for", "google", "look up"))?.let { query ->
            val ok = appLaunchManager.searchWeb(query)
            return CommandResult(if (ok) "Searching for $query." else "Search route failed.", JarvisMode.EXECUTING, ok)
        }

        if ("latest news" in command || "what is happening" in command || "world news" in command) {
            appLaunchManager.searchWeb("latest world technology news")
            return CommandResult("Routing latest world and technology news search.", JarvisMode.EXECUTING)
        }

        if ("help" in command || "commands" in command) {
            return CommandResult(
                "Available modules: open apps, search web, battery status, diagnostics, memory vault, callsign, tactical mode, stealth mode, red alert, and standby.",
                JarvisMode.ONLINE
            )
        }

        return CommandResult("Command parsed but no module accepted it yet. Try help for available commands.", JarvisMode.PROCESSING, executed = false)
    }
}
