package com.jarvis.core.device

import android.content.Context
import java.util.Locale

/**
 * Routes plain-language voice commands to app-launching or web-browsing actions.
 * Return null when this module does not recognize the command so Jarvis can pass
 * it to another subsystem.
 */
class DeviceCommandRouter(context: Context) {

    private val appLauncher = AppLauncher(context.applicationContext)
    private val webNavigator = WebNavigator(context.applicationContext)

    fun execute(command: String): String? {
        val normalized = command
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return null

        return when {
            isExplicitWebCommand(normalized) -> {
                webNavigator.describe(webNavigator.browse(normalized))
            }

            isOpenCommand(normalized) -> {
                val target = removeOpenPrefix(normalized)

                if (looksLikeWebsite(target)) {
                    webNavigator.describe(webNavigator.openWebsite(target))
                } else {
                    appLauncher.describe(appLauncher.openApp(target))
                }
            }

            else -> tryBareAppName(normalized)
        }
    }

    private fun tryBareAppName(command: String): String? {
        if (command.length > 48 || command.split(" ").size > 4) return null

        return when (val result = appLauncher.openApp(command)) {
            is AppLauncher.LaunchResult.NotFound -> null
            else -> appLauncher.describe(result)
        }
    }

    private fun isExplicitWebCommand(command: String): Boolean =
        command.startsWith("browse ") ||
            command.startsWith("browse for ") ||
            command.startsWith("search ") ||
            command.startsWith("search for ") ||
            command.startsWith("look up ")

    private fun isOpenCommand(command: String): Boolean =
        command.startsWith("open ") ||
            command.startsWith("launch ") ||
            command.startsWith("start ")

    private fun removeOpenPrefix(command: String): String = command
        .removePrefix("open ")
        .removePrefix("launch ")
        .removePrefix("start ")
        .trim()

    private fun looksLikeWebsite(value: String): Boolean =
        value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("www.") ||
            Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+([/?#].*)?$").matches(value)
}
