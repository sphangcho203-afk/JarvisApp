package com.seongja.jarvis

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CloudConfigActivity : Activity() {
    private data class ProfileFields(
        val model: EditText,
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )

    private lateinit var store: SecureCortexRegistry
    private lateinit var systemPromptInput: EditText
    private lateinit var globalStatus: TextView
    private val profileFields = linkedMapOf<String, ProfileFields>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureCortexRegistry(this)
        setContentView(buildUi())
        populate(store.load())
    }

    private fun buildUi(): ScrollView {
        val padding = dp(18)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(7, 12, 20))
        }

        root.addView(TextView(this).apply {
            text = "JARVIS // CORTEX MESH"
            textSize = 25f
            setTextColor(Color.rgb(64, 255, 226))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Ten encrypted cloud nodes: six Gemini projects and four Groq projects. Jarvis scores every healthy node by task fit, reliability, latency, freshness, and failure history, then automatically fails over when a node is limited or unavailable."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "Keys never enter GitHub. Endpoints are locked to the official HTTPS Gemini and Groq OpenAI-compatible gateways."
            textSize = 13f
            setTextColor(Color.rgb(145, 205, 255))
            setPadding(0, 0, 0, dp(16))
        })

        systemPromptInput = EditText(this).apply {
            hint = "System prompt"
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(Color.rgb(16, 28, 40), Color.rgb(42, 112, 124))
        }
        root.addView(systemPromptInput, matchWidth(bottom = 16))

        CortexDefaults.profiles().forEach { profile ->
            root.addView(buildProfilePanel(profile), matchWidth(bottom = 14))
        }

        globalStatus = TextView(this).apply {
            text = "MESH STATUS // NOT TESTED"
            textSize = 14f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(globalStatus)

        root.addView(Button(this).apply {
            text = "SAVE ALL 10 NODES"
            setOnClickListener { saveRegistry(showToast = true) }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "SAVE AND TEST ALL CONFIGURED NODES"
            setOnClickListener {
                if (saveRegistry(showToast = false)) testAllProfiles()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RESET CORTEX MESH"
            setOnClickListener {
                store.clear()
                populate(CortexRegistry())
                globalStatus.text = "MESH STATUS // RESET"
                Toast.makeText(this@CloudConfigActivity, "Cortex mesh reset.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RETURN TO JARVIS"
            setOnClickListener { finish() }
        }, matchWidth(bottom = 24))

        return ScrollView(this).apply { addView(root) }
    }

    private fun buildProfilePanel(profile: CortexProfile): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(
                fill = Color.rgb(12, 23, 35),
                stroke = if (profile.provider == CortexProvider.GEMINI) Color.rgb(43, 204, 187) else Color.rgb(166, 73, 202)
            )
        }

        panel.addView(TextView(this).apply {
            text = "${profile.label} // ${profile.provider.displayName.uppercase()}"
            textSize = 17f
            setTextColor(if (profile.provider == CortexProvider.GEMINI) Color.rgb(66, 255, 226) else Color.rgb(230, 112, 255))
        })

        panel.addView(TextView(this).apply {
            text = profile.provider.endpoint
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(0, dp(3), 0, dp(8))
        })

        val model = EditText(this).apply {
            hint = "Model ID from ${profile.provider.displayName} console"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            background = panelBackground(Color.rgb(18, 34, 49), Color.rgb(40, 78, 94))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        panel.addView(model, matchWidth(bottom = 8))

        val apiKey = EditText(this).apply {
            hint = "Encrypted API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            background = panelBackground(Color.rgb(18, 34, 49), Color.rgb(40, 78, 94))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        panel.addView(apiKey, matchWidth(bottom = 6))

        val enabled = CheckBox(this).apply {
            text = "Node enabled"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        panel.addView(enabled)

        val health = TextView(this).apply {
            text = "STATUS // NOT CONFIGURED"
            textSize = 12f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(5), 0, dp(5))
        }
        panel.addView(health)

        panel.addView(Button(this).apply {
            text = "SAVE + TEST ${profile.label}"
            setOnClickListener {
                if (saveRegistry(showToast = false)) testProfile(profile.id)
            }
        }, matchWidth())

        profileFields[profile.id] = ProfileFields(model, apiKey, enabled, health)
        return panel
    }

    private fun populate(registry: CortexRegistry) {
        systemPromptInput.setText(registry.systemPrompt)
        val byId = registry.profiles.associateBy { it.id }
        CortexDefaults.profiles().forEach { default ->
            val profile = byId[default.id] ?: default
            val fields = profileFields[default.id] ?: return@forEach
            fields.model.setText(profile.model)
            fields.apiKey.setText(profile.apiKey)
            fields.enabled.isChecked = profile.enabled
            fields.health.text = "STATUS // ${profile.healthLabel()} // SUCCESS ${profile.successes} // FAIL ${profile.failures}"
        }
        globalStatus.text = meshSummary(registry)
    }

    private fun saveRegistry(showToast: Boolean): Boolean {
        val current = store.load().profiles.associateBy { it.id }
        val updatedProfiles = CortexDefaults.profiles().map { default ->
            val previous = current[default.id] ?: default
            val fields = profileFields[default.id] ?: return@map previous
            previous.copy(
                model = fields.model.text.toString().trim(),
                apiKey = fields.apiKey.text.toString().trim(),
                enabled = fields.enabled.isChecked,
                lastError = if (
                    previous.model != fields.model.text.toString().trim() ||
                    previous.apiKey != fields.apiKey.text.toString().trim()
                ) "" else previous.lastError
            )
        }

        val registry = CortexRegistry(
            profiles = updatedProfiles,
            systemPrompt = systemPromptInput.text.toString().trim().ifBlank { CortexRegistry.DEFAULT_SYSTEM_PROMPT }
        )

        return runCatching {
            store.save(registry)
            globalStatus.text = meshSummary(store.load())
            if (showToast) Toast.makeText(this, "Cortex mesh saved securely.", Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            globalStatus.text = "MESH STATUS // SECURE STORAGE ERROR"
            Toast.makeText(this, "Could not store the mesh securely.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun testProfile(profileId: String) {
        val fields = profileFields[profileId] ?: return
        fields.health.text = "STATUS // TESTING..."
        Thread {
            val result = runCatching { CortexMeshClient(store).testProfile(profileId) }
            runOnUiThread {
                result.onSuccess {
                    fields.health.text = "STATUS // ONLINE // ${it.provider.displayName.uppercase()} // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                }.onFailure {
                    fields.health.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                globalStatus.text = meshSummary(store.load())
            }
        }.start()
    }

    private fun testAllProfiles() {
        val configured = store.load().configuredProfiles()
        if (configured.isEmpty()) {
            globalStatus.text = "MESH STATUS // ENTER AT LEAST ONE MODEL AND KEY"
            return
        }

        globalStatus.text = "MESH STATUS // TESTING ${configured.size} NODES..."
        Thread {
            var online = 0
            configured.forEachIndexed { index, profile ->
                runOnUiThread {
                    profileFields[profile.id]?.health?.text = "STATUS // TESTING ${index + 1}/${configured.size}..."
                }
                val result = runCatching { CortexMeshClient(store).testProfile(profile.id) }
                if (result.isSuccess) online++
                runOnUiThread {
                    val fields = profileFields[profile.id]
                    result.onSuccess {
                        fields?.health?.text = "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                    }.onFailure {
                        fields?.health?.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                    }
                    globalStatus.text = "MESH STATUS // TESTED ${index + 1}/${configured.size} // ONLINE $online"
                }
            }
            runOnUiThread {
                populate(store.load())
                globalStatus.text = "MESH STATUS // TEST COMPLETE // ONLINE $online/${configured.size}"
            }
        }.start()
    }

    private fun meshSummary(registry: CortexRegistry): String {
        val configured = registry.configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val cooling = configured.count { it.isCoolingDown() }
        return "MESH STATUS // CONFIGURED ${configured.size}/10 // ONLINE $online // COOLDOWN $cooling"
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun matchWidth(bottom: Int = 0): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, 0, dp(bottom)) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
