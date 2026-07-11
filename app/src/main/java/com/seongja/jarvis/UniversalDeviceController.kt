package com.seongja.jarvis

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale
import kotlin.math.max

internal data class DeviceCommandResult(
    val ok: Boolean,
    val reply: String,
    val intent: String,
    val details: String = ""
)

/** Android-side safe launcher and browser controller. */
internal class UniversalDeviceController(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    private data class LaunchableApp(
        val label: String,
        val normalizedLabel: String,
        val packageName: String,
        val activityName: String
    )

    private data class Candidate(val app: LaunchableApp, val score: Double)

    fun handle(rawInput: String): DeviceCommandResult? {
        val input = rawInput.trim()
        if (input.isBlank()) return null
        openPairingConsole(input)?.let { return it }
        webSearch(input)?.let { return it }
        explicitUrl(input)?.let { return it }
        launchAppCommand(input)?.let { return it }
        return null
    }

    fun installedAppSummary(limit: Int = 40): String {
        val apps = installedApps().sortedBy { it.label.lowercase(Locale.US) }
        if (apps.isEmpty()) return "No launchable applications were visible to Jarvis."
        val shown = apps.take(limit).joinToString("\n") { "• ${it.label}" }
        val remainder = apps.size - minOf(limit, apps.size)
        return buildString {
            appendLine("LAUNCHABLE APPS // ${apps.size}")
            append(shown)
            if (remainder > 0) append("\n… and $remainder more")
        }
    }

    private fun openPairingConsole(input: String): DeviceCommandResult? {
        val normalized = normalize(input)
        val phrases = setOf(
            "openpairing", "openpairingconsole", "openbridge",
            "openbridgesetup", "showpairing", "showbridgeconsole"
        )
        if (normalized !in phrases) return null
        return try {
            val intent = Intent(context, BridgeSetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceCommandResult(true, "Opening the secure bridge console, Sir.", "OPEN_PAIRING_CONSOLE")
        } catch (error: Exception) {
            DeviceCommandResult(false, "I could not open the bridge console, Sir.", "OPEN_PAIRING_CONSOLE", error.message.orEmpty())
        }
    }

    private fun webSearch(input: String): DeviceCommandResult? {
        val match = SEARCH_RE.matchEntire(input) ?: return null
        val query = match.groupValues[1].trim()
        if (query.isBlank()) return DeviceCommandResult(false, "Tell me what to search for, Sir.", "WEB_SEARCH")
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        return openWebUri(url, "Searching the web for $query, Sir.", "WEB_SEARCH")
    }

    private fun explicitUrl(input: String): DeviceCommandResult? {
        URL_RE.find(input)?.value?.let { rawUrl ->
            val cleaned = rawUrl.trim().trimEnd('.', ',', ')', ']', '}')
            val url = when {
                cleaned.startsWith("https://", true) -> cleaned
                cleaned.startsWith("http://", true) -> cleaned
                else -> "https://$cleaned"
            }
            return openWebUri(url, "Opening $cleaned, Sir.", "OPEN_URL")
        }
        val match = BROWSE_RE.matchEntire(input) ?: return null
        val target = match.groupValues[1].trim()
        if (!looksLikeHost(target)) return null
        val url = if (target.startsWith("http", true)) target else "https://$target"
        return openWebUri(url, "Opening $target, Sir.", "OPEN_URL")
    }

    private fun openWebUri(url: String, reply: String, intentName: String): DeviceCommandResult {
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase(Locale.US)
        if (parsed == null || scheme !in setOf("http", "https")) {
            return DeviceCommandResult(false, "I can open only normal HTTP or HTTPS addresses, Sir.", intentName)
        }
        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceCommandResult(true, reply, intentName, "url=$url")
        } catch (error: ActivityNotFoundException) {
            DeviceCommandResult(false, "No browser is available to open that address, Sir.", intentName, error.message.orEmpty())
        } catch (error: SecurityException) {
            DeviceCommandResult(false, "Android blocked that address, Sir.", intentName, error.message.orEmpty())
        }
    }

    private fun launchAppCommand(input: String): DeviceCommandResult? {
        val match = OPEN_APP_RE.matchEntire(input) ?: return null
        val requested = match.groupValues[1].trim().replace(Regex("(?i)\\s+app$"), "").trim()
        if (requested.isBlank()) return DeviceCommandResult(false, "Tell me which app to open, Sir.", "LAUNCH_APP")
        if (looksLikeHost(requested)) {
            val url = if (requested.startsWith("http", true)) requested else "https://$requested"
            return openWebUri(url, "Opening $requested, Sir.", "OPEN_URL")
        }
        if (PACKAGE_RE.matches(requested)) return launchPackage(requested, requested)

        val query = canonicalize(requested)
        val candidates = installedApps()
            .map { Candidate(it, appScore(query, it)) }
            .filter { it.score >= 0.56 }
            .sortedByDescending { it.score }

        if (candidates.isEmpty()) {
            return DeviceCommandResult(false, "I could not find a launchable app named $requested, Sir.", "LAUNCH_APP", "query=$requested")
        }
        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val exact = best.app.normalizedLabel == normalize(query)
        if (!exact && second != null && best.score - second.score < 0.06) {
            val names = candidates.take(3).joinToString(", ") { it.app.label }
            return DeviceCommandResult(false, "I found several close matches: $names. Say the full app name, Sir.", "LAUNCH_APP_AMBIGUOUS")
        }
        return launchApp(best.app)
    }

    private fun launchPackage(packageName: String, spokenName: String): DeviceCommandResult {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: return DeviceCommandResult(false, "That package has no normal launcher screen, Sir.", "LAUNCH_APP", "package=$packageName")
        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            DeviceCommandResult(true, "Opening $spokenName, Sir.", "LAUNCH_APP", "package=$packageName")
        } catch (error: Exception) {
            DeviceCommandResult(false, "I could not open $spokenName, Sir.", "LAUNCH_APP", error.message.orEmpty())
        }
    }

    private fun launchApp(app: LaunchableApp): DeviceCommandResult {
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
            }
        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            DeviceCommandResult(true, "Opening ${app.label}, Sir.", "LAUNCH_APP", "package=${app.packageName}")
        } catch (error: Exception) {
            DeviceCommandResult(false, "I found ${app.label}, but Android would not open it, Sir.", "LAUNCH_APP", error.message.orEmpty())
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<LaunchableApp> {
        val launcherQuery = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return packageManager.queryIntentActivities(launcherQuery, 0)
            .asSequence()
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                LaunchableApp(label, normalize(canonicalize(label)), activity.packageName, activity.name)
            }
            .filterNot { it.packageName == context.packageName }
            .distinctBy { it.packageName }
            .toList()
    }

    private fun appScore(queryRaw: String, app: LaunchableApp): Double {
        val query = normalize(queryRaw)
        val label = app.normalizedLabel
        if (query.isBlank() || label.isBlank()) return 0.0
        if (query == label) return 1.0
        if (label.startsWith(query) || query.startsWith(label)) return 0.93
        if (label.contains(query) || query.contains(label)) return 0.87
        val queryTokens = queryRaw.lowercase(Locale.US).split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }.toSet()
        val labelTokens = app.label.lowercase(Locale.US).split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }.toSet()
        val overlap = if (queryTokens.isEmpty()) 0.0 else queryTokens.intersect(labelTokens).size.toDouble() / queryTokens.size
        val edit = similarity(query, label)
        val packageBonus = if (normalize(app.packageName).contains(query)) 0.08 else 0.0
        return minOf(0.99, max(edit * 0.76, overlap * 0.82) + packageBonus)
    }

    private fun similarity(left: String, right: String): Double {
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / max(left.length, right.length).coerceAtLeast(1)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun canonicalize(value: String): String {
        val normalized = value.lowercase(Locale.US).trim()
        return ALIASES[normalize(normalized)] ?: normalized
    }

    private fun normalize(value: String): String = value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    private fun looksLikeHost(value: String): Boolean = HOST_RE.matches(value.trim()) || value.startsWith("http://", true) || value.startsWith("https://", true)

    companion object {
        private val OPEN_APP_RE = Regex("(?i)^\\s*(?:open|launch|start|run|bring\\s+up|show\\s+me)\\s+(?:the\\s+)?(?:app\\s+)?(.+?)\\s*$")
        private val SEARCH_RE = Regex("(?i)^\\s*(?:search(?:\\s+the\\s+web)?(?:\\s+for)?|google|look\\s+up|find\\s+online|browse\\s+for)\\s+(.+?)\\s*$")
        private val BROWSE_RE = Regex("(?i)^\\s*(?:open|visit|browse|go\\s+to)\\s+(?:the\\s+)?(?:website\\s+)?(.+?)\\s*$")
        private val URL_RE = Regex("(?i)(?:https?://|www\\.)[a-z0-9][a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*")
        private val HOST_RE = Regex("(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+(?:/.*)?$")
        private val PACKAGE_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+$")
        private val ALIASES = mapOf(
            "yt" to "youtube", "ytmusic" to "youtube music", "insta" to "instagram",
            "playstore" to "google play store", "play" to "google play store",
            "files" to "file manager", "filemanager" to "file manager"
        )
    }
}
