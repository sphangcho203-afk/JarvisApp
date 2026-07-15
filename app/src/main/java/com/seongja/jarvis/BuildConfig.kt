package com.seongja.jarvis

/**
 * Explicit application build flags for projects where Android Gradle build
 * config generation is disabled. WebView remote debugging remains off in
 * distributed APKs.
 */
internal object BuildConfig {
    const val DEBUG: Boolean = false
}
