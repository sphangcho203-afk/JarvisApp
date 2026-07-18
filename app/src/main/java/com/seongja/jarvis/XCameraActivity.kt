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
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * FRIDAY's explicit visual workspace. It never hides camera use: the preview,
 * capture state, active lens, and analysis state are always visible to the owner.
 */
class XCameraActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var resultView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var scanButton: Button
    private lateinit var switchButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var workspaceVoice: FridayWorkspaceVoice

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var question = DEFAULT_QUESTION
    private var autoScanRequested = true
    private var autoScanConsumed = false
    private var captureRunning = false

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bindCamera() else showFailure("Camera permission was denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        question = intent.getStringExtra(EXTRA_QUESTION).orEmpty().trim().ifBlank { DEFAULT_QUESTION }
        lensFacing = intent.getIntExtra(EXTRA_LENS, CameraSelector.LENS_FACING_BACK)
        autoScanRequested = intent.getBooleanExtra(EXTRA_AUTO_SCAN, true)
        cameraExecutor = Executors.newSingleThreadExecutor()
        workspaceVoice = FridayWorkspaceVoice(
            context = this,
            onDiagnostic = { message ->
                JarvisOperationBus.publish("X-CAMERA VOICE", message.take(180), .9f)
            }
        )
        setContentView(buildUi())
        XCameraRuntime.attach(this)
        JarvisOperationBus.publish("X-CAMERA", "OPTICAL ARRAY INITIALIZING", .08f)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            statusView.text = "X-CAMERA // CAMERA PERMISSION REQUIRED"
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        XCameraRuntime.detach(this)
        if (::workspaceVoice.isInitialized) workspaceVoice.destroy()
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }

    fun closeFromVoice() {
        runOnUiThread {
            if (::workspaceVoice.isInitialized) workspaceVoice.stop()
            finish()
        }
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(
            previewView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val scanLayer = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), BLUE)
                cornerRadius = dp(14).toFloat()
            }
        }
        root.addView(
            scanLayer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(18), dp(104), dp(18), dp(244))
            }
        )

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(12))
            background = panelBackground(alpha = 214)
        }
        top.addView(label("F.R.I.D.A.Y. // X-CAMERA", 20f, CYAN, true).apply {
            letterSpacing = .12f
        })
        top.addView(label("LIVE OPTICAL PERCEPTION // CAMERA USE VISIBLE", 9f, SOFT, true).apply {
            letterSpacing = .12f
        })
        statusView = label("X-CAMERA // INITIALIZING", 11f, GREEN, true)
        top.addView(statusView, params(top = 8))
        root.addView(
            top,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
            background = panelBackground(alpha = 238)
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        bottom.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))

        resultView = label(
            "Say ‘open your eyes’ to enter this workspace. FRIDAY captures only when SCAN is active.",
            11f,
            SOFT,
            false
        ).apply {
            setLineSpacing(0f, 1.16f)
        }
        bottom.addView(
            ScrollView(this).apply { addView(resultView) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)).apply {
                bottomMargin = dp(8)
            }
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        scanButton = button("SCAN WHAT I SEE", GREEN) { captureAndAnalyze() }
        switchButton = button("SWITCH LENS", BLUE) { switchLens() }
        val closeButton = button("CLOSE EYES", RED) {
            if (::workspaceVoice.isInitialized) workspaceVoice.stop()
            finish()
        }
        buttons.addView(scanButton, weightedButtonParams())
        buttons.addView(switchButton, weightedButtonParams())
        buttons.addView(closeButton, weightedButtonParams())
        bottom.addView(buttons)

        root.addView(
            bottom,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        )
        return root
    }

    private fun bindCamera() {
        statusView.text = "X-CAMERA // ACQUIRING OPTICAL SENSOR"
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(90)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
                imageCapture = capture
                statusView.text = "X-CAMERA // ${lensLabel()} EYE ONLINE"
                JarvisOperationBus.publish("X-CAMERA ONLINE", "${lensLabel()} OPTICAL FEED", .22f)
                if (autoScanRequested && !autoScanConsumed) {
                    autoScanConsumed = true
                    previewView.postDelayed({ captureAndAnalyze() }, 850L)
                }
            }.onFailure { showFailure("Camera startup failed: ${safeError(it)}") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchLens() {
        if (captureRunning) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        autoScanConsumed = true
        bindCamera()
    }

    private fun captureAndAnalyze() {
        val capture = imageCapture ?: run {
            showFailure("The optical sensor is not ready yet.")
            return
        }
        if (captureRunning) return
        captureRunning = true
        setBusy(true)
        statusView.text = "X-CAMERA // CAPTURING VISIBLE FRAME"
        resultView.text = "FRIDAY is looking through the ${lensLabel().lowercase(Locale.US)} camera."
        JarvisOperationBus.publish("X-CAMERA", "CAPTURING TEMPORARY FRAME", .36f)

        val file = File.createTempFile("friday-xcamera-", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
                    file.delete()
                    if (bytes.isEmpty()) {
                        runOnUiThread { showFailure("The captured camera frame was empty.") }
                        return
                    }
                    analyzeFrame(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    runOnUiThread { showFailure("Camera capture failed: ${safeError(exception)}") }
                }
            }
        )
    }

    private fun analyzeFrame(bytes: ByteArray) {
        JarvisOperationBus.publish("X-CAMERA", "GEMINI MULTIMODAL ANALYSIS", .62f)
        val result = runCatching {
            val client = GeminiVisionClient(this)
            if (!client.isConfigured()) {
                throw GeminiVisionException(
                    "No Gemini vision route is configured. Return to FRIDAY and say configure APIs."
                )
            }
            client.analyze(bytes, question = question)
        }

        runOnUiThread {
            setBusy(false)
            result.onSuccess { analysis ->
                statusView.text = "X-CAMERA // VISION VERIFIED // ${analysis.elapsedMs}MS"
                resultView.text = analysis.description
                val spokenInWorkspace = workspaceVoice.speak(analysis.description)
                XCameraRuntime.storeResult(question, analysis, spokenInWorkspace)
                JarvisConversationBus.recordUser("X-CAMERA: $question")
                JarvisConversationBus.recordAssistant(analysis.description)
                JarvisOperationBus.publish(
                    "X-CAMERA COMPLETE",
                    "${analysis.profileLabel} // ${analysis.model} // ${analysis.elapsedMs}MS",
                    1f
                )
                JarvisOperationBus.clear("OPTICAL RESULT READY")
            }.onFailure { showFailure(safeError(it)) }
        }
    }

    private fun setBusy(busy: Boolean) {
        captureRunning = busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        scanButton.isEnabled = !busy
        switchButton.isEnabled = !busy
    }

    private fun showFailure(message: String) {
        setBusy(false)
        statusView.text = "X-CAMERA // DEGRADED"
        resultView.text = message
        XCameraRuntime.storeSystemMessage(message)
        JarvisOperationBus.publish("X-CAMERA ERROR", message.take(220), 1f, false)
        JarvisOperationBus.clear("OPTICAL ROUTE FAILED")
    }

    private fun lensLabel(): String =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONT" else "REAR"

    private fun safeError(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(620)

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun button(text: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 9f
        letterSpacing = .04f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 13, 30))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun panelBackground(alpha: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(alpha, 1, 6, 17))
        setStroke(dp(1), BLUE)
    }

    private fun params(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun weightedButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_QUESTION = "friday_xcamera_question"
        private const val EXTRA_LENS = "friday_xcamera_lens"
        private const val EXTRA_AUTO_SCAN = "friday_xcamera_auto_scan"
        private const val DEFAULT_QUESTION =
            "Tell me what you can see, including important objects, visible text, and anything requiring attention."

        private val BG = Color.rgb(0, 3, 11)
        private val CYAN = Color.rgb(104, 222, 255)
        private val BLUE = Color.rgb(55, 121, 255)
        private val GREEN = Color.rgb(78, 231, 162)
        private val RED = Color.rgb(255, 89, 103)
        private val SOFT = Color.rgb(189, 213, 240)

        fun launch(
            context: Context,
            question: String = DEFAULT_QUESTION,
            frontCamera: Boolean = false,
            autoScan: Boolean = true
        ) {
            context.startActivity(
                Intent(context, XCameraActivity::class.java)
                    .putExtra(EXTRA_QUESTION, question.trim())
                    .putExtra(
                        EXTRA_LENS,
                        if (frontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    )
                    .putExtra(EXTRA_AUTO_SCAN, autoScan)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

data class XCameraPendingResult(
    val question: String,
    val description: String,
    val model: String,
    val elapsedMs: Long,
    val isError: Boolean = false,
    val spokenInWorkspace: Boolean = false
)

object XCameraRuntime {
    @Volatile
    private var activityRef: WeakReference<XCameraActivity>? = null
    @Volatile
    private var pending: XCameraPendingResult? = null

    fun attach(activity: XCameraActivity) {
        activityRef = WeakReference(activity)
    }

    fun detach(activity: XCameraActivity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    fun closeActive(): Boolean {
        val activity = activityRef?.get() ?: return false
        activity.closeFromVoice()
        return true
    }

    fun storeResult(
        question: String,
        result: VisionAnalysis,
        spokenInWorkspace: Boolean
    ) {
        pending = XCameraPendingResult(
            question = question,
            description = result.description,
            model = result.model,
            elapsedMs = result.elapsedMs,
            spokenInWorkspace = spokenInWorkspace
        )
    }

    fun storeSystemMessage(message: String) {
        pending = XCameraPendingResult(
            question = "X-CAMERA SYSTEM",
            description = message,
            model = "local",
            elapsedMs = 0L,
            isError = true
        )
    }

    fun consume(): XCameraPendingResult? {
        val result = pending
        pending = null
        return result
    }
}
