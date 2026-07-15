package com.seongja.jarvis

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Process-level API setup bridge for the HELIX WebView.
 *
 * Jarvis remains voice-first. This bridge exists only so API configuration is
 * reachable without turning the main interface into a text chatbot.
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
        attachApiSetupBridge(activity)
        openInitialSetupIfRequired(activity)
    }

    private fun attachApiSetupBridge(activity: MainActivity) {
        val webView = findWebView(activity.window.decorView) ?: return
        if (attachedWebViews.put(webView, true) == true) return

        webView.addJavascriptInterface(
            ApiSetupBridge(activity),
            API_SETUP_BRIDGE_NAME
        )
        webView.post {
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

    private class ApiSetupBridge(activity: MainActivity) {
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
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val API_SETUP_BRIDGE_NAME = "JarvisCommandBridge"
        private const val INITIAL_SETUP_DELAY_MS = 650L
    }
}
