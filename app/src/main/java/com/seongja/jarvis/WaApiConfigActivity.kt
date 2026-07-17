package com.seongja.jarvis

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
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

class WaApiConfigActivity : Activity() {
    private lateinit var store: WaApiSecureStore
    private lateinit var tokenField: EditText
    private lateinit var instanceField: EditText
    private lateinit var enabledField: CheckBox
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WaApiSecureStore(this)
        setContentView(buildUi())
        populate()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(text("F.R.I.D.A.Y. // WAAPI DIRECT", 22f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .12f
        }, matchWidth(6))
        root.addView(text("ENCRYPTED WHATSAPP INSTANCE LINK", 11f, SOFT, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .16f
        }, matchWidth(18))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = panelBackground()
        }
        panel.addView(text(
            "WaAPI uses the WhatsApp account already paired in your dashboard. The bearer token stays encrypted inside Android Keystore storage. Leave Instance ID blank and F.R.I.D.A.Y. will discover the first connected instance automatically.",
            12f,
            MUTED,
            false
        ), matchWidth(12))

        tokenField = input("WaAPI bearer token", secret = true)
        instanceField = input("Instance ID (optional auto-discovery)", secret = false).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        enabledField = CheckBox(this).apply {
            text = "WaAPI direct messaging enabled"
            setTextColor(SOFT)
            textSize = 14f
            buttonTintList = android.content.res.ColorStateList.valueOf(CYAN)
        }
        statusView = text("STATUS // INITIALIZING", 12f, SOFT, true).apply {
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = outlineBackground(BLUE)
        }

        panel.addView(tokenField, matchWidth(8))
        panel.addView(instanceField, matchWidth(6))
        panel.addView(enabledField, matchWidth(8))
        panel.addView(statusView, matchWidth(10))
        panel.addView(button("SAVE + DISCOVER + TEST") {
            if (save(showToast = false)) testConnection()
        }, matchWidth(8))
        panel.addView(button("SAVE CONFIGURATION") { save(showToast = true) }, matchWidth(8))
        panel.addView(button("CLEAR WAAPI CREDENTIALS") {
            store.clear()
            populate()
            Toast.makeText(this, "WaAPI credentials cleared.", Toast.LENGTH_SHORT).show()
        }, matchWidth(0))
        root.addView(panel, matchWidth(14))

        root.addView(text(
            "VOICE COMMANDS // CONFIGURE WHATSAPP API // WHATSAPP API STATUS // SEND WHATSAPP TO +COUNTRYCODE NUMBER SAYING MESSAGE // CONFIRM",
            11f,
            MUTED,
            true
        ), matchWidth(14))
        root.addView(button("RETURN TO F.R.I.D.A.Y.") { finish() }, matchWidth(0))
        return ScrollView(this).apply { addView(root) }
    }

    private fun populate() {
        val settings = store.load()
        tokenField.setText(settings.apiToken)
        instanceField.setText(settings.instanceId.takeIf { it > 0L }?.toString().orEmpty())
        enabledField.isChecked = settings.enabled
        statusView.text = buildString {
            append("STATUS // ${settings.healthLabel()}")
            append(" // OK ${settings.successes}")
            append(" // FAIL ${settings.failures}")
            if (settings.lastError.isNotBlank()) append("\n${settings.lastError.take(220)}")
        }
    }

    private fun save(showToast: Boolean): Boolean = runCatching {
        val previous = store.load()
        val token = tokenField.text.toString().trim()
        val instanceId = instanceField.text.toString().trim().toLongOrNull() ?: 0L
        val changed = token != previous.apiToken || instanceId != previous.instanceId
        store.save(
            previous.copy(
                apiToken = token,
                instanceId = instanceId,
                enabled = enabledField.isChecked,
                lastStatusCode = if (changed) 0 else previous.lastStatusCode,
                lastClientState = if (changed) "" else previous.lastClientState,
                lastError = if (changed) "" else previous.lastError
            )
        )
        populate()
        if (showToast) Toast.makeText(this, "WaAPI configuration encrypted and saved.", Toast.LENGTH_SHORT).show()
        true
    }.getOrElse { error ->
        statusView.text = "STATUS // SAVE FAILED // ${error.message ?: error.javaClass.simpleName}"
        false
    }

    private fun testConnection() {
        statusView.text = "STATUS // DISCOVERING CONNECTED INSTANCE..."
        Thread {
            val result = runCatching { WaApiClient(store).testConnection() }
            runOnUiThread {
                result.onSuccess {
                    statusView.text = "STATUS // ONLINE // INSTANCE ${it.instanceId} // ${it.clientState.uppercase()} // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                    instanceField.setText(it.instanceId.toString())
                }.onFailure {
                    statusView.text = "STATUS // FAILED // ${(it.message ?: it.javaClass.simpleName).take(260)}"
                }
                populate()
            }
        }.start()
    }

    private fun input(hint: String, secret: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(MUTED)
        setTextColor(Color.WHITE)
        textSize = 14f
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = outlineBackground(BLUE)
        inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        isAllCaps = false
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = .06f
        background = outlineBackground(CYAN)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(PANEL)
        setStroke(dp(1), CYAN)
        cornerRadius = dp(2).toFloat()
    }

    private fun outlineBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(PANEL)
        setStroke(dp(1), color)
        cornerRadius = dp(2).toFloat()
    }

    private fun matchWidth(bottom: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFF02070D.toInt()
        private const val PANEL = 0xFF071522.toInt()
        private const val CYAN = 0xFF49E6FF.toInt()
        private const val BLUE = 0xFF287CFF.toInt()
        private const val SOFT = 0xFFC8D9E8.toInt()
        private const val MUTED = 0xFF71879A.toInt()
    }
}
