package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Finds and opens launcher activities installed on the Android device.
 *
 * Keep this class small and permission-light. It does not use Accessibility
 * Services and does not tap inside other apps.
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

    private val aliases = mapOf(
        "mlbb" to "mobile legends",
        "mobile legend" to "mobile legends",
        "yt" to "youtube",
        "chrome browser" to "chrome",
        "playstore" to "play store",
        "playstore app" to "play store",
        "insta" to "instagram",
        "wa" to "whatsapp"
    )

    fun openApp(spokenName: String): LaunchResult {
        val requested = normalizeAlias(spokenName)
        if (requested.isBlank()) return LaunchResult.NotFound(spokenName)

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

        val matches = activities.mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(context.packageManager)
                ?.toString()
                ?.trim()
                ?: return@mapNotNull null

            val score = calculateScore(requested, normalize(label))
            if (score <= 0) return@mapNotNull null

            AppMatch(
                label = label,
                packageName = resolveInfo.activityInfo.packageName,
                score = score
            )
        }
            .distinctBy { it.packageName }
            .sortedByDescending { it.score }

        if (matches.isEmpty()) return LaunchResult.NotFound(spokenName)

        val bestScore = matches.first().score
        val closeMatches = matches.filter { bestScore - it.score <= 5 }.take(5)

        if (closeMatches.size > 1 && bestScore < 100) {
            return LaunchResult.MultipleMatches(closeMatches)
        }

        return openPackage(matches.first().packageName, matches.first().label)
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
            "I found several matches: $options. Say the exact app name."
        }
    }

    private fun normalizeAlias(input: String): String {
        val normalized = normalize(
            input
                .removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("start ")
        )
        return aliases[normalized] ?: normalized
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun calculateScore(requested: String, installed: String): Int = when {
        requested == installed -> 100
        installed.startsWith(requested) -> 90
        requested.startsWith(installed) -> 85
        installed.contains(requested) -> 75
        requested.contains(installed) -> 70
        requested.split(" ").all { it in installed } -> 60
        else -> 0
    }
}
