package com.seongja.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var hudView: JarvisHudView
    private lateinit var engine: JarvisEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hudView = JarvisHudView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(hudView)

        engine = JarvisEngine(this) { state -> hudView.render(state) }
        engine.initialize()

        hudView.setOnClickListener {
            ensureMicPermissionThenListen()
        }

        ensureMicPermission()
    }

    private fun ensureMicPermissionThenListen() {
        if (hasMicPermission()) {
            engine.toggleListening()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    private fun ensureMicPermission() {
        if (!hasMicPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}
