package com.seongja.jarvis

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class BridgeSetupActivity : Activity() {
    private val bridge by lazy { LocalBridgeClient(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText
    private lateinit var pairButton: Button
    private lateinit var commandInput: EditText
    private lateinit var sendButton: Button
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildInterface())
        refreshPairingState()
    }

    override fun onResume() {
        super.onResume()
        refreshPairingState()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildInterface(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
            setBackgroundColor(BG)
        }

        root.addView(label("JARVIS // SECURE BRIDGE", 23f, ACCENT, true))
        root.addView(label("PHASE 7.1 // MANUAL PAIRING + COMMAND CONSOLE", 12f, MUTED, false).apply {
            setPadding(0, dp(6), 0, dp(18))
        })

        statusText = label("PAIRING STATUS // CHECKING", 15f, WARNING, true)
        root.addView(panel(statusText))

        root.addView(sectionTitle("01 // PAIR DEVICE"))
        root.addView(label(
            "In Termux run: jarvis-pair\nThe six-digit code is copied to your clipboard and expires after ten minutes.",
            13f,
            TEXT,
            false
        ))

        codeInput = EditText(this).apply {
            hint = "6-digit pair code"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 22f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            typeface = Typeface.MONOSPACE
            background = fieldBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(codeInput, fullWidth(top = 10))

        val pairingButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        pairingButtons.addView(actionButton("PASTE CODE") { pastePairCode() }, weighted())
        pairButton = actionButton("PAIR DEVICE") { submitPairCode() }
        pairingButtons.addView(pairButton, weighted(left = 8))
        root.addView(pairingButtons, fullWidth(top = 10))

        val unpair = actionButton("CLEAR SAVED PAIRING") {
            bridge.clearPairing()
            resultText.text = "Saved bridge token removed."
            refreshPairingState()
        }
        root.addView(unpair, fullWidth(top = 8))

        root.addView(sectionTitle("02 // TYPED COMMAND FALLBACK"))
        root.addView(label(
            "This console bypasses speech recognition. Use it to verify that Android, Termux and the local bridge can communicate.",
            13f,
            TEXT,
            false
        ))

        commandInput = EditText(this).apply {
            hint = "Example: battery status"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 16f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = fieldBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(commandInput, fullWidth(top = 10))

        sendButton = actionButton("SEND THROUGH LOCAL BRIDGE") { submitTypedCommand() }
        root.addView(sendButton, fullWidth(top = 10))

        resultText = label("CONSOLE // READY", 13f, TEXT, false).apply {
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(panel(resultText), fullWidth(top = 12))

        root.addView(sectionTitle("03 // QUICK TEST SEQUENCE"))
        root.addView(label(
            "1. Run jarvis-pair in Termux.\n" +
                "2. Paste the code above.\n" +
                "3. Tap PAIR DEVICE.\n" +
                "4. Send: battery status.\n" +
                "5. Send: open youtube.",
            13f,
            TEXT,
            false
        ))

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
    }

    private fun submitPairCode() {
        val code = codeInput.text?.toString().orEmpty().filter(Char::isDigit)
        if (code.length != 6) {
            showResult("PAIRING ERROR // Enter exactly six digits.", false)
            return
        }

        setBusy(true, "PAIRING STATUS // AUTHENTICATING")
        worker.execute {
            val result = bridge.pair(code)
            main.post {
                setBusy(false)
                showResult(result.message, result.ok)
                if (result.ok) codeInput.text?.clear()
                refreshPairingState()
            }
        }
    }

    private fun submitTypedCommand() {
        val command = commandInput.text?.toString().orEmpty().trim()
        if (command.isBlank()) {
            showResult("COMMAND ERROR // Type a command first.", false)
            return
        }
        if (!bridge.isPaired()) {
            showResult("COMMAND BLOCKED // Pair the bridge first.", false)
            refreshPairingState()
            return
        }

        setBusy(true, "BRIDGE // PROCESSING COMMAND")
        worker.execute {
            val decision = bridge.ask(command)
            val error = bridge.lastError
            main.post {
                setBusy(false)
                if (decision == null) {
                    showResult("BRIDGE ERROR // $error", false)
                    refreshPairingState()
                } else {
                    val report = buildString {
                        appendLine("JARVIS // ${decision.reply}")
                        appendLine()
                        appendLine("INTENT // ${decision.intent}")
                        appendLine("EXECUTION // ${if (decision.executionOk) "SUCCESS" else "NOT COMPLETED"}")
                        append("LATENCY // ${decision.elapsedMs} ms")
                    }
                    showResult(report, decision.executionOk)
                }
            }
        }
    }

    private fun pastePairCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val value = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
            .filter(Char::isDigit)
            .take(6)

        if (value.length == 6) {
            codeInput.setText(value)
            codeInput.setSelection(value.length)
            showResult("PAIR CODE // PASTED", true)
        } else {
            Toast.makeText(this, "Clipboard does not contain a six-digit code.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPairingState() {
        val paired = bridge.isPaired()
        statusText.text = if (paired) {
            "PAIRING STATUS // PAIRED\nBRIDGE // 127.0.0.1:8765"
        } else {
            "PAIRING STATUS // UNPAIRED\nRUN // jarvis-pair"
        }
        statusText.setTextColor(if (paired) SUCCESS else WARNING)
        commandInput.isEnabled = paired
        sendButton.isEnabled = paired
        sendButton.alpha = if (paired) 1f else 0.45f
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        pairButton.isEnabled = !busy
        sendButton.isEnabled = !busy && bridge.isPaired()
        if (message != null) resultText.text = message
    }

    private fun showResult(message: String, ok: Boolean) {
        resultText.text = message
        resultText.setTextColor(if (ok) SUCCESS else WARNING)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(0f, 1.12f)
        }

    private fun sectionTitle(text: String): TextView = label(text, 15f, ACCENT, true).apply {
        setPadding(0, dp(24), 0, dp(8))
    }

    private fun panel(child: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBackground()
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(child, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(BG)
        textSize = 12f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        background = buttonBackground()
        setOnClickListener { action() }
        minHeight = dp(48)
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(PANEL)
        setStroke(dp(1), BORDER)
        cornerRadius = dp(12).toFloat()
    }

    private fun fieldBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(FIELD)
        setStroke(dp(1), BORDER)
        cornerRadius = dp(10).toFloat()
    }

    private fun buttonBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(ACCENT)
        cornerRadius = dp(10).toFloat()
    }

    private fun fullWidth(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

    private fun weighted(left: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(left)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.rgb(3, 9, 14)
        private val PANEL = Color.rgb(7, 20, 29)
        private val FIELD = Color.rgb(4, 14, 21)
        private val BORDER = Color.rgb(20, 104, 126)
        private val ACCENT = Color.rgb(83, 226, 255)
        private val TEXT = Color.rgb(220, 247, 255)
        private val MUTED = Color.rgb(116, 161, 176)
        private val SUCCESS = Color.rgb(99, 255, 166)
        private val WARNING = Color.rgb(255, 190, 84)
    }
}
