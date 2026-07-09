package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var engine: JarvisEngine
    private lateinit var sound: SoundEngine
    private lateinit var voice: VoiceManager
    private lateinit var hud: JarvisHudView
    private lateinit var prefs: JarvisPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        prefs = JarvisPreferences(this).apply {
            lastBootMillis = System.currentTimeMillis()
        }
        engine = JarvisEngine()
        sound = SoundEngine(this)
        val actions = SystemActions(this)
        val router = CommandRouter(actions, prefs)
        voice = VoiceManager(this, engine, router, sound)
        hud = JarvisHudView(this, engine).apply {
            onTap = { toggleVoiceLoop() }
        }

        setContentView(hud)
        engine.update(
            status = "READY",
            response = "Phase 2 interface online. Tap once to toggle the voice loop.",
            listening = false,
            mode = "BOOT",
            commandCount = prefs.commandCount,
            lastCommand = prefs.lastCommand,
            signal = "LOCAL"
        )
        ensureMicPermission()
    }

    private fun ensureMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    private fun toggleVoiceLoop() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.toggle()
        } else {
            ensureMicPermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice control.", Toast.LENGTH_LONG).show()
            engine.update(status = "MIC LOCKED", response = "Microphone permission denied. Voice systems cannot run without it.", listening = false, mode = "LOCKED")
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        voice.destroy()
        sound.shutdown()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }
}
