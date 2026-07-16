package com.seongja.jarvis

import android.text.Html
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class YouTubeDataException(message: String) : Exception(message)

sealed class YouTubeCommand {
    data class Search(val query: String) : YouTubeCommand()
    data object Trending : YouTubeCommand()
}

data class YouTubeVideoResult(
    val title: String,
    val channel: String,
    val videoId: String,
    val publishedAt: String
)

data class YouTubeDataResult(
    val command: YouTubeCommand,
    val videos: List<YouTubeVideoResult>,
    val statusCode: Int,
    val elapsedMs: Long
) {
    fun spoken(): String {
        if (videos.isEmpty()) return "I could not find any matching YouTube videos, Sir."
        val lead = when (command) {
            is YouTubeCommand.Search -> "I found ${videos.size} YouTube results."
            YouTubeCommand.Trending -> "I found ${videos.size} currently popular YouTube videos in India."
        }
        return "$lead The first is ${videos.first().title}, from ${videos.first().channel}. The full list is on screen."
    }

    fun display(): String = buildString {
        appendLine(
            when (command) {
                is YouTubeCommand.Search -> "YOUTUBE DATA // VERIFIED SEARCH"
                YouTubeCommand.Trending -> "YOUTUBE DATA // INDIA TRENDING"
            }
        )
        appendLine("RESULTS // ${videos.size} // HTTP $statusCode // ${elapsedMs}ms")
        appendLine()
        videos.forEachIndexed { index, video ->
            appendLine("${index + 1}. ${video.title}")
            appendLine("CHANNEL // ${video.channel}")
            appendLine("VIDEO ID // ${video.videoId}")
            if (video.publishedAt.isNotBlank()) appendLine("PUBLISHED // ${video.publishedAt}")
            if (index != videos.lastIndex) appendLine()
        }
    }.trim()
}

/** Public-data YouTube search. Private account data remains OAuth-gated. */
class YouTubeDataClient(private val store: SecureIntegrationRegistry) {
    fun isConfigured(): Boolean = store.load().isYouTubeConfigured()

    fun execute(command: YouTubeCommand): YouTubeDataResult {
        val settings = store.load()
        if (!settings.isYouTubeConfigured()) {
            throw YouTubeDataException("YouTube Data API v3 is not configured.")
        }
        val endpoint = when (command) {
            is YouTubeCommand.Search -> buildSearchUrl(command.query, settings.youtubeApiKey)
            YouTubeCommand.Trending -> buildTrendingUrl(settings.youtubeApiKey)
        }
        val started = System.currentTimeMillis()
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Friday-Android/0.9.22")
        }
        try {
            val status = connection.responseCode
            val raw = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val message = apiError(raw)
                store.recordYouTubeFailure(message, status)
                throw YouTubeDataException("YouTube HTTP $status: $message")
            }
            val videos = parseVideos(raw, command)
            store.recordYouTubeSuccess(elapsed, status)
            return YouTubeDataResult(command, videos, status, elapsed)
        } catch (error: YouTubeDataException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            store.recordYouTubeFailure(message)
            throw YouTubeDataException(message)
        } finally {
            connection.disconnect()
        }
    }

    fun test(): YouTubeDataResult = execute(YouTubeCommand.Search("Android technology"))

    private fun buildSearchUrl(query: String, key: String): String = buildString {
        append(SEARCH_ENDPOINT)
        append("?part=snippet&type=video&maxResults=5&safeSearch=strict&regionCode=IN&q=")
        append(encode(query.take(160)))
        append("&key=")
        append(encode(key.trim()))
    }

    private fun buildTrendingUrl(key: String): String = buildString {
        append(VIDEOS_ENDPOINT)
        append("?part=snippet&chart=mostPopular&maxResults=5&regionCode=IN&key=")
        append(encode(key.trim()))
    }

    private fun parseVideos(raw: String, command: YouTubeCommand): List<YouTubeVideoResult> {
        val array = JSONObject(raw).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                val videoId = when (command) {
                    is YouTubeCommand.Search -> item.optJSONObject("id")?.optString("videoId").orEmpty()
                    YouTubeCommand.Trending -> item.optString("id")
                }
                if (videoId.isBlank()) continue
                add(
                    YouTubeVideoResult(
                        title = decodeHtml(snippet.optString("title")).ifBlank { "Untitled video" },
                        channel = decodeHtml(snippet.optString("channelTitle")).ifBlank { "Unknown channel" },
                        videoId = videoId,
                        publishedAt = snippet.optString("publishedAt")
                    )
                )
            }
        }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun apiError(raw: String): String = runCatching {
        val error = JSONObject(raw).optJSONObject("error")
        error?.optString("message")?.takeIf { it.isNotBlank() } ?: raw.take(280)
    }.getOrDefault(raw.take(280)).ifBlank { "Unknown API error" }

    @Suppress("DEPRECATION")
    private fun decodeHtml(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val SEARCH_ENDPOINT = "https://www.googleapis.com/youtube/v3/search"
        private const val VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"

        fun commandFor(rawInput: String): YouTubeCommand? {
            val input = rawInput.trim()
            val lower = input.lowercase()
            val trending = setOf(
                "youtube trending",
                "trending on youtube",
                "what is trending on youtube",
                "what's trending on youtube",
                "show youtube trending"
            )
            if (lower in trending) return YouTubeCommand.Trending
            val prefixes = listOf(
                "search youtube for ",
                "youtube search ",
                "find youtube videos about ",
                "find videos on youtube about ",
                "search for videos on youtube about "
            )
            prefixes.firstOrNull(lower::startsWith)?.let { prefix ->
                val query = input.substring(prefix.length).trim().trimEnd('.', '?', '!')
                if (query.isNotBlank()) return YouTubeCommand.Search(query)
            }
            return null
        }
    }
}
