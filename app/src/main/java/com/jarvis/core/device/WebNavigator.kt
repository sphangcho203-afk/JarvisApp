package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Opens URLs and browser searches using Android ACTION_VIEW intents. */
class WebNavigator(private val context: Context) {

    sealed class NavigationResult {
        data class Opened(val destination: String) : NavigationResult()
        data class Failed(val message: String) : NavigationResult()
    }

    fun browse(request: String): NavigationResult {
        val cleaned = cleanRequest(request)
        if (cleaned.isBlank()) {
            return NavigationResult.Failed("Tell me what you want to browse.")
        }

        val target = if (looksLikeWebsite(cleaned)) {
            normalizeUrl(cleaned)
        } else {
            val encoded = URLEncoder.encode(
                cleaned,
                StandardCharsets.UTF_8.toString()
            )
            "https://www.google.com/search?q=$encoded"
        }

        return openUri(target, cleaned)
    }

    fun openWebsite(url: String): NavigationResult {
        val cleaned = url.trim()
        if (cleaned.isBlank()) {
            return NavigationResult.Failed("The website address is empty.")
        }
        return openUri(normalizeUrl(cleaned), cleaned)
    }

    private fun openUri(uriString: String, label: String): NavigationResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            NavigationResult.Opened(label)
        } catch (error: Exception) {
            NavigationResult.Failed(
                "I couldn't open $label: ${error.message ?: "no compatible browser found"}."
            )
        }
    }

    fun describe(result: NavigationResult): String = when (result) {
        is NavigationResult.Opened -> "Opening ${result.destination}."
        is NavigationResult.Failed -> result.message
    }

    private fun cleanRequest(value: String): String = value
        .trim()
        .removePrefix("browse for ")
        .removePrefix("browse ")
        .removePrefix("search for ")
        .removePrefix("search ")
        .removePrefix("look up ")
        .trim()

    private fun looksLikeWebsite(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("www.", ignoreCase = true) ||
            Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+([/?#].*)?$").matches(value)

    private fun normalizeUrl(value: String): String = when {
        value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("https://", ignoreCase = true) -> value
        else -> "https://$value"
    }
}
