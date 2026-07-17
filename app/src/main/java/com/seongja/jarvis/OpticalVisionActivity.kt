package com.seongja.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class OpticalVisionActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var targetView: TextView
    private lateinit var responseView: TextView
    private lateinit var stateView: TextView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val analyzingFrame = AtomicBoolean(false)
    private var labels: List<VisionLabel> = emptyList()
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        brain = JarvisBrain(this)
        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = { partial -> runOnUiThread { stateView.text = partial.take(120) } },
            onRms = {},
            onState = { state -> runOnUiThread { stateView.text = "VOICE // ${state.uppercase(Locale.US)}" } },
            onDiagnostic = {}
        )
        setContentView(buildUi())
        OpticalVisionRuntime.attach(this)
        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceLoop.resume()
        }
    }

    override fun onPause() {
        resumed = false
        voiceLoop.stop()
        super.onPause()
    }

    override fun onDestroy() {
        OpticalVisionRuntime.detach(this)
        analyzerExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_AND_MICROPHONE) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                responseView.text = "Camera permission is required for Optical Vision."
            }
            if (resumed && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceLoop.resume()
            }
        }
    }

    fun closeVision(reason: String) {
        stateView.text = "OPTICAL SYSTEMS // OFFLINE"
        responseView.text = "Visual channel closed. $reason"
        voiceLoop.stop()
        finish()
    }

    private fun ensureCameraPermission() {
        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.isEmpty()) startCamera()
        else requestPermissions(permissions.toTypedArray(), REQUEST_CAMERA_AND_MICROPHONE)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val labeler = ImageLabeling.getClient(
                    ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(.55f)
                        .build()
                )
                analysis.setAnalyzer(analyzerExecutor) { proxy ->
                    if (!analyzingFrame.compareAndSet(false, true)) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        analyzingFrame.set(false)
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                    labeler.process(image)
                        .addOnSuccessListener { incoming ->
                            labels = incoming
                                .sortedByDescending { it.confidence }
                                .take(6)
                                .map { VisionLabel(it.text, it.confidence) }
                            runOnUiThread { renderLabels() }
                        }
                        .addOnFailureListener { error ->
                            runOnUiThread {
                                stateView.text = "VISION ERROR // ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                        .addOnCompleteListener {
                            proxy.close()
                            analyzingFrame.set(false)
                        }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                stateView.text = "OPTICAL SYSTEMS // ONLINE"
            }.onFailure { error ->
                responseView.text = "Camera initialization failed: ${error.message ?: error.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderLabels() {
        if (labels.isEmpty()) {
            targetView.text = "TARGET // SEARCHING"
            return
        }
        targetView.text = buildString {
            appendLine("TARGET ESTIMATE")
            labels.forEach { label ->
                appendLine("${label.name.uppercase(Locale.US)} // ${(label.confidence * 100).toInt()}%")
            }
        }.trim()
    }

    private fun handleSpeech(raw: String) {
        val clean = raw.trim()
        if (clean.isBlank()) return
        val lower = clean.lowercase(Locale.getDefault())
        when {
            lower.contains("sleep jarvis") || lower.contains("sleep friday") -> {
                FridaySessionController.sleep(this, "VOICE COMMAND")
                return
            }
            lower.contains("close camera") || lower.contains("stop camera") -> {
                closeVision("OWNER COMMAND")
                return
            }
        }

        val observed = labels.joinToString { "${it.name} ${(it.confidence * 100).toInt()} percent" }
            .ifBlank { "No reliable object labels are available yet" }
        responseView.text = "Analyzing the current camera field..."
        voiceLoop.pauseForProcessing()
        Thread {
            val prompt = buildString {
                appendLine("The owner is using F.R.I.D.A.Y. Optical Vision.")
                appendLine("Current on-device camera labels: $observed.")
                appendLine("The labels are estimates, not guaranteed facts. Do not identify a real person.")
                appendLine("Owner request: $clean")
                append("Answer naturally and distinguish confirmed visual evidence from inference.")
            }
            val response = runCatching { brain.respond(prompt) }.getOrElse { error ->
                BrainResponse(
                    spoken = "Visual analysis failed: ${error.message ?: error.javaClass.simpleName}",
                    display = "VISUAL ANALYSIS FAILED // ${error.message ?: error.javaClass.simpleName}",
                    intent = "vision/error",
                    confidence = 0f,
                    mode = BrainMode.ALERT,
                    trace = listOf("optical_vision", "analysis_failed"),
                    memory = brain.memorySnapshot(),
                    thoughts = emptyList(),
                    entities = emptyList(),
                    decision = "report_vision_failure",
                    action = BrainAction()
                )
            }
            runOnUiThread {
                responseView.text = response.display.ifBlank { response.spoken }
                if (resumed) voiceLoop.resumeAfterTts(700L)
            }
        }.start()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(196, 1, 8, 18))
                setStroke(dp(1), CYAN)
            }
        }
        top.addView(label("F.R.I.D.A.Y. // OPTICAL VISION", 16f, CYAN, true), params(bottom = 4))
        stateView = label("OPTICAL SYSTEMS // INITIALIZING", 9f, SOFT, true)
        top.addView(stateView, params(bottom = 7))
        targetView = label("TARGET // SEARCHING", 10f, Color.WHITE, false)
        top.addView(targetView)
        root.addView(top, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply { setMargins(dp(10), dp(10), dp(10), 0) })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(224, 1, 8, 18))
                setStroke(dp(1), BLUE)
                cornerRadius = dp(8).toFloat()
            }
        }
        responseView = label(
            "Point the camera at an object, then ask: What is this? or Analyze it.",
            11f,
            SOFT,
            false
        ).apply { maxLines = 8 }
        bottom.addView(responseView, params(bottom = 8))
        bottom.addView(button("CLOSE OPTICAL VISION", RED) { closeVision("OWNER CONTROL") })
        root.addView(bottom, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(dp(10), 0, dp(10), dp(12)) })
        return root
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(0f, 1.14f)
    }

    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 9f
        letterSpacing = .07f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(5, 17, 34))
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
        private const val REQUEST_CAMERA_AND_MICROPHONE = 6501
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val RED = Color.rgb(255, 92, 106)
        private val SOFT = Color.rgb(181, 211, 242)

        fun launch(context: Context) {
            context.startActivity(
                Intent(context, OpticalVisionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}

data class VisionLabel(val name: String, val confidence: Float)
