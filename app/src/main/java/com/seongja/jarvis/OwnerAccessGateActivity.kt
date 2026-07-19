package com.seongja.jarvis

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class OwnerAccessGateActivity : FragmentActivity() {
    private var resolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(
            TextView(this).apply {
                text = "F.R.I.D.A.Y. // OWNER AUTHENTICATION\n\nConfirm your device credential to open this protected console."
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Color.rgb(210, 244, 255))
                setBackgroundColor(Color.rgb(2, 7, 14))
                setPadding(36, 36, 36, 36)
            }
        )
        authenticate()
    }

    private fun authenticate() {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            reject()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    if (resolved) return
                    resolved = true
                    OwnerAccessController.grant()
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    reject()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock F.R.I.D.A.Y. protected console")
                .setSubtitle("Owner authentication is required")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun reject() {
        if (resolved) return
        resolved = true
        OwnerAccessController.deny()
        finish()
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !resolved) reject()
        super.onDestroy()
    }
}
