package com.seongja.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CloudConfigActivity : Activity() {
    private lateinit var store: SecureCloudConfigStore
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var systemPromptInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureCloudConfigStore(this)
        setContentView(buildUi())
        populate(store.load())
    }

    private fun buildUi(): ScrollView {
        val padding = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(10, 15, 23))
        }

        root.addView(TextView(this).apply {
            text = "JARVIS // CLOUD CORTEX"
            textSize = 24f
            setTextColor(Color.rgb(61, 255, 226))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Localhost and Termux brain endpoints are disabled. Enter an HTTPS OpenAI-compatible chat-completions endpoint. Your key is encrypted with Android Keystore and is never committed to GitHub."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(18))
        })

        endpointInput = field(
            label = "API endpoint",
            hint = "https://provider.example/v1/chat/completions",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        modelInput = field(
            label = "Model",
            hint = "provider model ID",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        apiKeyInput = field(
            label = "API key",
            hint = "Stored encrypted on this device",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        systemPromptInput = field(
            label = "System prompt",
            hint = CloudConfig.DEFAULT_SYSTEM_PROMPT,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            minLines = 5
        )

        root.addView(endpointInput)
        root.addView(modelInput)
        root.addView(apiKeyInput)
        root.addView(systemPromptInput)

        statusText = TextView(this).apply {
            text = "STATUS // NOT TESTED"
            textSize = 14f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = "SAVE CONFIGURATION"
            setOnClickListener {
                saveConfiguration(showToast = true)
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "SAVE AND TEST CONNECTION"
            setOnClickListener {
                if (saveConfiguration(showToast = false)) testConnection()
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "CLEAR CLOUD CONFIGURATION"
            setOnClickListener {
                store.clear()
                populate(CloudConfig())
                statusText.text = "STATUS // CONFIGURATION CLEARED"
                Toast.makeText(this@CloudConfigActivity, "Cloud configuration cleared.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "RETURN TO JARVIS"
            setOnClickListener { finish() }
        }, matchWidth())

        return ScrollView(this).apply { addView(root) }
    }

    private fun field(
        label: String,
        hint: String,
        inputType: Int,
        minLines: Int = 1
    ): EditText = EditText(this).apply {
        this.hint = "$label\n$hint"
        this.inputType = inputType
        this.minLines = minLines
        setTextColor(Color.WHITE)
        setHintTextColor(Color.rgb(115, 135, 150))
        setBackgroundColor(Color.rgb(18, 29, 42))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(12))
        layoutParams = params
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, 0, dp(10)) }

    private fun populate(config: CloudConfig) {
        endpointInput.setText(config.endpoint)
        modelInput.setText(config.model)
        apiKeyInput.setText(config.apiKey)
        systemPromptInput.setText(config.systemPrompt)
        statusText.text = if (config.isConfigured()) {
            "STATUS // CONFIGURED // MODEL ${config.model}"
        } else {
            "STATUS // CONFIGURATION REQUIRED"
        }
    }

    private fun saveConfiguration(showToast: Boolean): Boolean {
        val config = CloudConfig(
            endpoint = endpointInput.text.toString().trim(),
            model = modelInput.text.toString().trim(),
            apiKey = apiKeyInput.text.toString().trim(),
            systemPrompt = systemPromptInput.text.toString().trim().ifBlank { CloudConfig.DEFAULT_SYSTEM_PROMPT }
        )

        val error = validate(config)
        if (error != null) {
            statusText.text = "STATUS // ERROR // $error"
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return false
        }

        return runCatching {
            store.save(config)
            statusText.text = "STATUS // SAVED // MODEL ${config.model}"
            if (showToast) Toast.makeText(this, "Cloud configuration saved.", Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            statusText.text = "STATUS // SECURE STORAGE ERROR"
            Toast.makeText(this, "Could not store the configuration securely.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun validate(config: CloudConfig): String? {
        if (config.endpoint.isBlank()) return "Enter the API endpoint."
        if (!config.endpoint.startsWith("https://", ignoreCase = true)) return "Only HTTPS endpoints are allowed."
        val lower = config.endpoint.lowercase()
        if (lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("0.0.0.0")) {
            return "Local server endpoints are disabled."
        }
        if (config.model.isBlank()) return "Enter the provider's model ID."
        return null
    }

    private fun testConnection() {
        statusText.text = "STATUS // TESTING CLOUD CONNECTION..."
        setInputsEnabled(false)
        Thread {
            val result = runCatching { CloudBrainClient(store).testConnection() }
            runOnUiThread {
                setInputsEnabled(true)
                result.onSuccess {
                    statusText.text = "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms\n${it.reply.take(180)}"
                }.onFailure {
                    statusText.text = "STATUS // TEST FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
            }
        }.start()
    }

    private fun setInputsEnabled(enabled: Boolean) {
        endpointInput.isEnabled = enabled
        modelInput.isEnabled = enabled
        apiKeyInput.isEnabled = enabled
        systemPromptInput.isEnabled = enabled
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
