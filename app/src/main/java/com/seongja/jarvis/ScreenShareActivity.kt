package com.seongja.jarvis

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ScreenShareActivity : Activity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusView: TextView
    private val stateListener: (ScreenVisionSnapshot) -> Unit = { snapshot ->
        runOnUiThread { statusView.text = snapshot.truthfulAnswer() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        setContentView(buildUi())
        ScreenVisionRuntime.addListener(stateListener)
        ScreenVisionRuntime.refreshAccessibility(this)
    }

    override fun onDestroy() {
        ScreenVisionRuntime.removeListener(stateListener)
        super.onDestroy()
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    ScreenCaptureService.start(this, resultCode, data)
                    Toast.makeText(this, "Screen-sharing consent granted for this session.", Toast.LENGTH_LONG).show()
                    maybeStartFloatingHelix()
                    finish()
                } else {
                    ScreenVisionRuntime.refreshAccessibility(this)
                    Toast.makeText(this, "Screen sharing was not started.", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_OVERLAY -> maybeStartFloatingHelix()
        }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(title("F.R.I.D.A.Y. // SCREEN VISION"), params(bottom = 5))
        root.addView(center("USER-CONSENTED VISUAL CHANNEL", 9f, MUTED, true), params(bottom = 14))

        val statusPanel = panel()
        statusPanel.addView(label("TRUTHFUL VISIBILITY STATE", 10f, CYAN, true), params(bottom = 7))
        statusView = label(ScreenVisionRuntime.snapshot().truthfulAnswer(), 12f, SOFT, false)
        statusPanel.addView(statusView)
        root.addView(statusPanel, params(bottom = 10))

        root.addView(panel().apply {
            addView(label("ANDROID CONSENT", 11f, GREEN, true), params(bottom = 6))
            addView(label(
                "Android displays its own screen-capture confirmation. F.R.I.D.A.Y. cannot skip, hide, or reuse consent for a new MediaProjection session.",
                11f,
                SOFT,
                false
            ))
        }, params(bottom = 10))

        root.addView(button("START SCREEN SHARING", CYAN) { requestProjection() }, params(bottom = 7))
        root.addView(button("ENABLE FLOATING HELIX", BLUE) { requestOverlay() }, params(bottom = 7))
        root.addView(button("STOP VISUAL CHANNEL", RED) {
            FridaySessionController.stopVisualChannels(this, "OWNER STOP")
            statusView.text = ScreenVisionRuntime.snapshot().truthfulAnswer()
        }, params(bottom = 7))
        root.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        return ScrollView(this).apply { addView(root) }
    }

    private fun requestProjection() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_PROJECTION)
    }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            FloatingHelixService.show(this, "STANDING BY")
            return
        }
        startActivityForResult(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ),
            REQUEST_OVERLAY
        )
    }

    private fun maybeStartFloatingHelix() {
        if (Settings.canDrawOverlays(this)) {
            FloatingHelixService.show(this, "VISUAL LINK ACTIVE")
        }
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 12, 28))
            setStroke(dp(1), BLUE)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun title(text: String) = center(text, 21f, CYAN, true).apply { letterSpacing = .08f }

    private fun center(text: String, size: Float, color: Int, bold: Boolean) =
        label(text, size, color, bold).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(0f, 1.16f)
    }

    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 10f
        letterSpacing = .06f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 18, 39))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PROJECTION = 6401
        private const val REQUEST_OVERLAY = 6402
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 106)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: android.content.Context) {
            context.startActivity(
                Intent(context, ScreenShareActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
