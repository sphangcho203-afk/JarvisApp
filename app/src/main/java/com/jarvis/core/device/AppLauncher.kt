package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.util.Locale
import kotlin.math.min

/**
 * Discovers launchable apps dynamically and resolves natural spoken names.
 *
 * No fixed package list is required. The registry is rebuilt periodically so
 * newly installed or removed apps become available without an app update.
 */
class AppLauncher(private val context: Context) {

    data class AppMatch(
        val label: String,
        val packageName: String,
        val score: Int
    )

    sealed class LaunchResult {
        data class Opened(val appName: String) : LaunchResult()
        data class MultipleMatches(val apps: List<AppMatch>) : LaunchResult()
        data class NotFound(val requestedName: String) : LaunchResult()
        data class Failed(val message: String) : LaunchResult()
    }

    private data class InstalledApp(
        val label: String,
        val normalizedLabel: String,
        val packageName: String
    )

    private val aliases = mapOf(
        "mlbb" to listOf("mobile legends", "mobile legends bang bang"),
        "mobile legend" to listOf("mobile legends", "mobile legends bang bang"),
        "yt" to listOf("youtube"),
        "youtube music" to listOf("yt music", "youtube music"),
        "chrome browser" to listOf("chrome", "google chrome"),
        "browser" to listOf("chrome", "browser"),
        "playstore" to listOf("play store", "google play store"),
        "play store" to listOf("play store", "google play store"),
        "insta" to listOf("instagram"),
        "wa" to listOf("whatsapp"),
        "google pay" to listOf("gpay", "google pay"),
        "g pay" to listOf("gpay", "google pay"),
        "files" to listOf("files", "files by google"),
        "photos" to listOf("google photos", "photos")
    )

    @Volatile
    private var cachedApps: List<InstalledApp> = emptyList()

    @Volatile
    private var cacheCreatedAtMs: Long = 0L

    fun openApp(spokenName: String): LaunchResult {
        val requested = cleanRequestedName(spokenName)
        if (requested.isBlank()) return LaunchResult.NotFound(spokenName)

        // Last-resort safety gate. The device router should already reject
        // knowledge questions, but this prevents a sentence from ever matching
        // a tiny app label such as "X" through substring scoring.
        if (looksLikeKnowledgeRequest(requested)) {
            return LaunchResult.NotFound(spokenName)
        }

        val variants = requestedVariants(requested)
        val matches = installedApps().mapNotNull { app ->
            val score = variants.maxOf { variant -> calculateScore(variant, app) }
            if (score < MINIMUM_MATCH_SCORE) return@mapNotNull null
            AppMatch(app.label, app.packageName, score)
        }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<AppMatch> { it.score }.thenBy { it.label.length })

        if (matches.isEmpty()) return LaunchResult.NotFound(spokenName)

        val best = matches.first()
        val closeMatches = matches
            .filter { best.score - it.score <= AMBIGUITY_WINDOW }
            .take(5)

        if (closeMatches.size > 1 && best.score < EXACT_MATCH_SCORE) {
            return LaunchResult.MultipleMatches(closeMatches)
        }

        return openPackage(best.packageName, best.label)
    }

    fun openPackage(packageName: String, displayName: String = packageName): LaunchResult {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.Failed(
                "$displayName is installed, but Android did not expose a launcher activity."
            )

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(launchIntent)
            LaunchResult.Opened(displayName)
        } catch (error: Exception) {
            LaunchResult.Failed("I couldn't open $displayName: ${error.message ?: "unknown error"}")
        }
    }

    fun describe(result: LaunchResult): String = when (result) {
        is LaunchResult.Opened -> "Opening ${result.appName}."
        is LaunchResult.NotFound -> "I couldn't find an installed app named ${result.requestedName}."
        is LaunchResult.Failed -> result.message
        is LaunchResult.MultipleMatches -> {
            val options = result.apps.joinToString(", ") { it.label }
            "I found several close matches: $options. Say the exact app name."
        }
    }

    fun invalidateRegistry() {
        cachedApps = emptyList()
        cacheCreatedAtMs = 0L
    }

    private fun installedApps(): List<InstalledApp> {
        val now = SystemClock.elapsedRealtime()
        val current = cachedApps
        if (current.isNotEmpty() && now - cacheCreatedAtMs < CACHE_TTL_MS) return current

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val rebuilt = activities.mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(context.packageManager)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val packageName = resolveInfo.activityInfo?.packageName
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            InstalledApp(label, normalize(label), packageName)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        cachedApps = rebuilt
        cacheCreatedAtMs = now
        return rebuilt
    }

    private fun requestedVariants(requested: String): List<String> = buildList {
        add(requested)
        aliases[requested].orEmpty().forEach(::add)
        aliases.entries
            .filter { (_, values) -> requested in values }
            .forEach { (alias, _) -> add(alias) }
    }.map(::normalize).filter { it.isNotBlank() }.distinct()

    private fun cleanRequestedName(input: String): String {
        var value = normalize(input)
        value = value.replace(Regex("^(?:the\\s+)?"), "")
        value = value.replace(Regex("\\s+(?:application|app)$"), "")
        return value.trim()
    }

    private fun looksLikeKnowledgeRequest(requested: String): Boolean {
        val tokens = requested.split(" ").filter { it.isNotBlank() }
        if (QUESTION_PREFIXES.any(requested::startsWith)) return true
        if (tokens.size > 5) return true
        return tokens.size >= 3 && KNOWLEDGE_TERMS.containsMatchIn(requested)
    }

    private fun calculateScore(requested: String, app: InstalledApp): Int {
        val installed = app.normalizedLabel
        if (requested == installed) return EXACT_MATCH_SCORE

        // Short app labels need strict matching. Without this, a one-letter app
        // such as X can hijack unrelated sentences that merely contain that letter.
        val safeRequested = requested.length >= 2
        val safeInstalled = installed.length >= 3

        if (safeRequested && installed.startsWith(requested)) return 94
        if (safeInstalled && requested.startsWith(installed)) return 91
        if (requested.length >= 3 && installed.contains(requested)) return 86
        if (safeInstalled && requested.contains(installed)) return 82

        val requestedTokens = requested.split(" ")
            .filter { it.length >= 2 }
            .toSet()
        val installedTokens = installed.split(" ")
            .filter { it.length >= 2 }
            .toSet()
        val shared = requestedTokens.intersect(installedTokens).size
        if (shared > 0 && requestedTokens.isNotEmpty()) {
            val coverage = shared.toFloat() / requestedTokens.size
            if (coverage >= 1f) return 79
            if (coverage >= 0.66f) return 70
        }

        val compactRequested = requested.replace(" ", "")
        val compactInstalled = installed.replace(" ", "")
        val maxLength = maxOf(compactRequested.length, compactInstalled.length)
        if (maxLength < 3 || maxLength > 32) return 0
        val distance = levenshtein(compactRequested, compactInstalled)
        val similarity = 1f - distance.toFloat() / maxLength
        return when {
            similarity >= 0.88f -> 76
            similarity >= 0.78f && minOf(compactRequested.length, compactInstalled.length) >= 5 -> 64
            else -> 0
        }
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                val insertion = current[rightIndex] + 1
                val deletion = previous[rightIndex + 1] + 1
                val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] = min(insertion, min(deletion, substitution))
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val CACHE_TTL_MS = 15_000L
        private const val EXACT_MATCH_SCORE = 100
        private const val MINIMUM_MATCH_SCORE = 60
        private const val AMBIGUITY_WINDOW = 4

        private val QUESTION_PREFIXES = listOf(
            "what ", "whats ", "why ", "how ", "when ", "where ", "who ",
            "tell me ", "explain ", "research ", "find out ", "is ", "are ",
            "do ", "does ", "did ", "can ", "could ", "would ", "should "
        )

        private val KNOWLEDGE_TERMS = Regex(
            "\\b(happening|happened|latest|today|news|world|current|recent|explain|research|information|know|meaning|reason|compare|summarize|summary)\\b"
        )
    }
}
