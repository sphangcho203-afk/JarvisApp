package com.seongja.jarvis

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.jarvis.core.device.DeviceCommandRouter

/**
 * Minimal exported command gateway for Termux.
 * It only forwards app-launch and web-navigation commands to DeviceCommandRouter.
 */
class TermuxCommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent?.getStringExtra(EXTRA_COMMAND)
            ?.trim()
            .orEmpty()

        if (command.isBlank()) {
            Toast.makeText(this, "No Jarvis command received.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val message = DeviceCommandRouter(applicationContext).execute(command)
            ?: "That is not an app-launch or web-navigation command."

        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_COMMAND = "command"
    }
}
