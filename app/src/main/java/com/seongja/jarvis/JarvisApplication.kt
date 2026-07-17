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
 * Process-level setup, owner-access policy, and live-data bridge for HELIX.
 *
 * First launch flows through cortex/API setup, WeatherAPI setup, then Android
 * permissions. Secrets never cross the JavaScript bridge; HELIX receives only
 * redacted health and weather data.
 */
class JarvisApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val attachedWebViews = WeakHashMap<WebView, Boolean>()
    private var apiSetupOpenedThisProcess = false
    private var weatherSetupOpenedThisProcess = false
    private var permissionSetupOpenedThisProcess = false

    override fun onCreate() {
        super.onCreate()
        WeatherRuntime.initialize(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        OwnerAccessController.protect(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // API 28 fallback because onActivityPreCreated was added in API 29.
        OwnerAccessController.protect(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        OwnerAccessController.protect(activity)
        if (OwnerAccessController.requireAuthentication(activity)) return
        if (activity !is MainActivity) return

        attachSetupBridge(activity)
        WeatherRuntime.refresh()
        val cortexConfigured = runCatching { JarvisBrain(activity).isCloudConfigured() }
            .getOrDefault(false)
        when {
            !cortexConfigured -> openInitialApiSetupIfRequired(activity)
            !WeatherSetupActivity.isOnboardingComplete(activity) ->
                openWeatherSetupIfRequired(activity)
            else -> openPermissionSetupIfRequired(activity)
        }
    }

    private fun attachSetupBridge(activity: MainActivity) {
        val webView = findWebView(activity.window.decorView) ?: return
        if (attachedWebViews.put(webView, true) == true) return

        webView.addJavascriptInterface(
            SetupBridge(activity),
            SETUP_BRIDGE_NAME
        )
        webView.post {
            if (!activity.isFinishing && !activity.isDestroyed) webView.reload()
        }
    }

    private fun openInitialApiSetupIfRequired(activity: MainActivity) {
        if (apiSetupOpenedThisProcess || activity.isFinishing || activity.isDestroyed) return
        apiSetupOpenedThisProcess = true
        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(Intent(activity, CloudConfigActivity::class.java))
            }
        }, INITIAL_SETUP_DELAY_MS)
    }

    private fun openWeatherSetupIfRequired(activity: MainActivity) {
        if (weatherSetupOpenedThisProcess || activity.isFinishing || activity.isDestroyed) return
        weatherSetupOpenedThisProcess = true
        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(Intent(activity, WeatherSetupActivity::class.java))
            }
        }, WEATHER_SETUP_DELAY_MS)
    }

    private fun openPermissionSetupIfRequired(activity: MainActivity) {
        if (permissionSetupOpenedThisProcess ||
            PermissionCenterActivity.isOnboardingComplete(activity) ||
            activity.isFinishing ||
            activity.isDestroyed
        ) {
            return
        }
        permissionSetupOpenedThisProcess = true
        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(Intent(activity, PermissionCenterActivity::class.java))
            }
        }, PERMISSION_SETUP_DELAY_MS)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private class SetupBridge(activity: MainActivity) {
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
        fun openPermissionCenter() {
            val activity = activityRef.get() ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(Intent(activity, PermissionCenterActivity::class.java))
                }
            }
        }

        @JavascriptInterface
        fun openWeatherSetup() {
            val activity = activityRef.get() ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(Intent(activity, WeatherSetupActivity::class.java))
                }
            }
        }

        @JavascriptInterface
        fun getWeatherJson(): String = WeatherRuntime.bridgeJson()

        @JavascriptInterface
        fun refreshWeather() {
            WeatherRuntime.refresh(force = true)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val SETUP_BRIDGE_NAME = "JarvisCommandBridge"
        private const val INITIAL_SETUP_DELAY_MS = 650L
        private const val WEATHER_SETUP_DELAY_MS = 550L
        private const val PERMISSION_SETUP_DELAY_MS = 550L
    }
}
