package com.seongja.jarvis

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageGenerationActivity : Activity() {
    private lateinit var promptView: TextView
    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var imageView: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var shareButton: Button
    private lateinit var regenerateButton: Button
    private var prompt: String = ""
    private var savedUri: Uri? = null
    private var generationRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().trim()
        setContentView(buildUi())
        if (prompt.isBlank()) {
            statusView.text = "IMAGE SYNTHESIS // PROMPT REQUIRED"
            detailView.text = "Tell F.R.I.D.A.Y. what image to create."
            progress.visibility = View.GONE
        } else {
            generate()
        }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // IMAGE SYNTHESIS", 22f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .09f
        }, params(bottom = 5))
        root.addView(label("GEMINI VISUAL INTELLIGENCE // OWNER REQUEST", 10f, SOFT, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .14f
        }, params(bottom = 14))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelBackground()
        }
        statusView = label("IMAGE SYNTHESIS // INITIALIZING", 14f, GREEN, true)
        promptView = label(prompt.ifBlank { "NO PROMPT" }, 12f, SOFT, false)
        detailView = label("Selecting an available Gemini image route.", 11f, MUTED, false)
        progress = ProgressBar(this).apply { isIndeterminate = true }
        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(1, 7, 18))
            visibility = View.GONE
        }
        shareButton = button("SHARE GENERATED IMAGE", BLUE) { shareImage() }.apply { isEnabled = false }
        regenerateButton = button("GENERATE AGAIN", GREEN) { generate() }.apply { isEnabled = false }

        panel.addView(statusView, params(bottom = 8))
        panel.addView(promptView, params(bottom = 8))
        panel.addView(detailView, params(bottom = 10))
        panel.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(8) })
        panel.addView(imageView, params(bottom = 10))
        panel.addView(shareButton, params(bottom = 7))
        panel.addView(regenerateButton, params(bottom = 7))
        panel.addView(button("RETURN TO F.R.I.D.A.Y.", CYAN) { finish() })
        root.addView(panel, params())
        return ScrollView(this).apply { addView(root) }
    }

    private fun generate() {
        if (generationRunning || prompt.isBlank()) return
        generationRunning = true
        savedUri = null
        shareButton.isEnabled = false
        regenerateButton.isEnabled = false
        imageView.visibility = View.GONE
        progress.visibility = View.VISIBLE
        statusView.text = "IMAGE SYNTHESIS // GENERATING"
        detailView.text = "Gemini visual route acquisition in progress."
        JarvisOperationBus.publish("IMAGE SYNTHESIS", "GENERATING VISUAL // GEMINI ROUTE", .18f)

        Thread {
            val result = runCatching {
                val client = GeminiImageClient(this)
                if (!client.isConfigured()) {
                    throw GeminiImageException("No Gemini key is configured. Say configure APIs and add a Gemini key first.")
                }
                client.generate(prompt)
            }
            runOnUiThread {
                generationRunning = false
                progress.visibility = View.GONE
                result.onSuccess(::displayGeneratedImage)
                    .onFailure(::displayFailure)
            }
        }.start()
    }

    private fun displayGeneratedImage(result: GeneratedImage) {
        val bitmap = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        if (bitmap == null) {
            displayFailure(GeminiImageException("Android could not decode the generated image."))
            return
        }
        imageView.setImageBitmap(bitmap)
        imageView.visibility = View.VISIBLE
        savedUri = saveToGallery(result)
        statusView.text = "IMAGE SYNTHESIS // COMPLETE"
        detailView.text = buildString {
            append("MODEL // ${result.model}\n")
            append("TIME // ${result.elapsedMs}ms\n")
            append("FORMAT // ${result.mimeType.uppercase(Locale.US)}")
            if (savedUri != null) append("\nSAVED // PICTURES/FRIDAY")
            if (result.text.isNotBlank()) append("\nNOTE // ${result.text.take(180)}")
        }
        shareButton.isEnabled = savedUri != null
        regenerateButton.isEnabled = true
        JarvisOperationBus.publish("IMAGE SYNTHESIS COMPLETE", "${result.model} // ${result.elapsedMs}MS", 1f)
        JarvisOperationBus.clear("IMAGE READY")
    }

    private fun displayFailure(error: Throwable) {
        val message = (error.message ?: error.javaClass.simpleName)
            .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
        statusView.text = "IMAGE SYNTHESIS // FAILED"
        detailView.text = message
        regenerateButton.isEnabled = true
        JarvisOperationBus.publish("IMAGE SYNTHESIS ERROR", message, 1f, false)
        JarvisOperationBus.clear("IMAGE ROUTE FAILED")
    }

    private fun saveToGallery(result: GeneratedImage): Uri? = runCatching {
        val extension = when {
            result.mimeType.contains("jpeg", true) || result.mimeType.contains("jpg", true) -> "jpg"
            result.mimeType.contains("webp", true) -> "webp"
            else -> "png"
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FRIDAY_$stamp.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, result.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FRIDAY")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android MediaStore rejected the image.")
        contentResolver.openOutputStream(uri)?.use { it.write(result.bytes) }
            ?: throw IllegalStateException("Android could not open the image destination.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        }
        uri
    }.getOrElse {
        Toast.makeText(this, "Image preview ready, but gallery save failed.", Toast.LENGTH_LONG).show()
        null
    }

    private fun shareImage() {
        val uri = savedUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Created by F.R.I.D.A.Y. // $prompt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share F.R.I.D.A.Y. image"))
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(0f, 1.14f)
        }

    private fun button(text: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 11f
        letterSpacing = .08f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 18, 39))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(4, 12, 28))
        setStroke(dp(1), BLUE)
        cornerRadius = dp(7).toFloat()
    }

    private fun params(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_PROMPT = "friday_image_prompt"
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: Context, prompt: String) {
            context.startActivity(
                Intent(context, ImageGenerationActivity::class.java)
                    .putExtra(EXTRA_PROMPT, prompt.trim())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
