package com.seongja.jarvis

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Process-level recovery bridge for the HELIX WebView.
 *
 * The bridge gives the bundled interface a command path that does not depend on
 * microphone recognition. It also guarantees that an unconfigured installation
 * reaches the API registry on first launch.
 */
class JarvisApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val attachedWebViews = WeakHashMap<WebView, Boolean>()
    private var setupOpenedThisProcess = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return
        attachCommandBridge(activity)
        openInitialSetupIfRequired(activity)
    }

    private fun attachCommandBridge(activity: MainActivity) {
        val webView = findWebView(activity.window.decorView) ?: return
        if (attachedWebViews.put(webView, true) == true) return

        webView.addJavascriptInterface(
            CommandBridge(activity),
            COMMAND_BRIDGE_NAME
        )
        webView.post {
            // addJavascriptInterface is guaranteed to be visible after the next
            // page load. Reload exactly once for this WebView instance.
            if (!activity.isFinishing && !activity.isDestroyed) webView.reload()
        }
    }

    private fun openInitialSetupIfRequired(activity: MainActivity) {
        if (setupOpenedThisProcess || activity.isFinishing || activity.isDestroyed) return
        val configured = runCatching { JarvisBrain(activity).isCloudConfigured() }
            .getOrDefault(false)
        if (configured) return

        setupOpenedThisProcess = true
        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(Intent(activity, CloudConfigActivity::class.java))
            }
        }, INITIAL_SETUP_DELAY_MS)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private class CommandBridge(activity: MainActivity) {
        private val activityRef = WeakReference(activity)

        @JavascriptInterface
        fun openApiSetup() {
            val activity = activityRef.get() ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(Intent(activity, CloudConfigActivity::class.java))
                }
            }
        }

        @JavascriptInterface
        fun onTextCommand(text: String) {
            val clean = text
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_COMMAND_CHARS)
            if (clean.isBlank()) return

            if (isApiSetupCommand(clean)) {
                openApiSetup()
                return
            }

            val activity = activityRef.get() ?: return
            activity.runOnUiThread {
                val delivered = runCatching {
                    activity.javaClass
                        .getDeclaredMethod("handleSpeech", String::class.java)
                        .apply { isAccessible = true }
                        .invoke(activity, clean)
                }.isSuccess

                if (!delivered) {
                    Toast.makeText(
                        activity,
                        "Jarvis typed-command channel could not initialize.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        private fun isApiSetupCommand(text: String): Boolean {
            val normalized = text
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val mentionsApi = normalized.split(' ').any { it == "api" || it == "apis" }
            val setupIntent = listOf("configure", "connect", "setup", "setting", "settings")
                .any(normalized::contains)
            return mentionsApi && setupIntent
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val COMMAND_BRIDGE_NAME = "JarvisCommandBridge"
        private const val INITIAL_SETUP_DELAY_MS = 650L
        private const val MAX_COMMAND_CHARS = 2_000
    }
}
