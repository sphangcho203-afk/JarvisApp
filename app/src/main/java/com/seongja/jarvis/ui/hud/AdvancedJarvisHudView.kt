package com.seongja.jarvis.ui.hud

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.seongja.jarvis.models.JarvisMode
import com.seongja.jarvis.models.JarvisUiState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AdvancedJarvisHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var uiState = JarvisUiState(isBooting = true, bootProgress = 0f)
    private var phase = 0f
    private val startTime = SystemClock.elapsedRealtime()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE }
    private val arcBounds = RectF()
    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        animator.start()
    }

    fun render(state: JarvisUiState) {
        uiState = state
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawVoid(canvas)
        drawGrid(canvas)
        drawParticles(canvas)
        if (uiState.isBooting) drawBootSequence(canvas) else drawMainHud(canvas)
    }

    private fun drawVoid(canvas: Canvas) {
        paint.shader = RadialGradient(
            width / 2f,
            height / 2f,
            width * 0.75f,
            intArrayOf(0xFF071A2B.toInt(), 0xFF02060F.toInt(), Color.BLACK),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawGrid(canvas: Canvas) {
        val color = withAlpha(uiState.mode.primaryColor, 42)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = color
        val spacing = 42f
        val offset = (phase / 360f) * spacing
        var x = -spacing + offset
        while (x < width + spacing) {
            canvas.drawLine(x, 0f, x + width * 0.22f, height.toFloat(), paint)
            x += spacing
        }
        var y = -spacing + offset
        while (y < height + spacing) {
            canvas.drawLine(0f, y, width.toFloat(), y + height * 0.08f, paint)
            y += spacing
        }
    }

    private fun drawParticles(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        val count = 42
        for (i in 0 until count) {
            val t = (SystemClock.elapsedRealtime() - startTime) / 1000f
            val x = ((i * 97 + t * (8 + i % 5)) % width)
            val y = ((i * 173 + t * (3 + i % 7)) % height)
            paint.color = withAlpha(if (i % 4 == 0) uiState.mode.secondaryColor else uiState.mode.primaryColor, 70)
            canvas.drawCircle(x, y, if (i % 5 == 0) 2.8f else 1.5f, paint)
        }
    }

    private fun drawBootSequence(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        drawReactor(canvas, cx, cy, min(width, height) * 0.22f, JarvisMode.PROCESSING)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = uiState.mode.secondaryColor
        textPaint.textSize = 26f
        canvas.drawText("JARVIS CORE INITIALIZING", cx, cy + min(width, height) * 0.32f, textPaint)
        drawProgressBar(canvas, width * 0.18f, cy + min(width, height) * 0.37f, width * 0.64f, 18f, uiState.bootProgress)

        textPaint.textSize = 13f
        textPaint.textAlign = Paint.Align.LEFT
        val lines = listOf(
            "BOOT: quantum ui matrix .......... ${percent(uiState.bootProgress)}",
            "VOICE: command channel ........... calibrating",
            "MEMORY: local vault .............. sealed",
            "SECURITY: permission lattice ..... scanning",
            "STATUS: advanced civilization layer online"
        )
        val start = cy + min(width, height) * 0.43f
        lines.forEachIndexed { index, line ->
            textPaint.color = withAlpha(uiState.mode.primaryColor, if (index <= uiState.bootProgress * lines.size) 230 else 82)
            canvas.drawText(line, width * 0.10f, start + index * 28f, textPaint)
        }
    }

    private fun drawMainHud(canvas: Canvas) {
        val cx = width / 2f
        val cy = height * 0.42f
        val radius = min(width, height) * 0.26f
        drawReactor(canvas, cx, cy, radius, uiState.mode)
        drawVoiceWave(canvas, cx, cy + radius + 65f, width * 0.66f)
        drawTelemetryPanels(canvas)
        drawCommandStrip(canvas)
        drawStatusHeader(canvas)
    }

    private fun drawReactor(canvas: Canvas, cx: Float, cy: Float, radius: Float, mode: JarvisMode) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        for (i in 0..5) {
            val r = radius * (0.38f + i * 0.13f)
            arcBounds.set(cx - r, cy - r, cx + r, cy + r)
            paint.strokeWidth = if (i % 2 == 0) 3.8f else 1.5f
            paint.color = withAlpha(if (i % 2 == 0) mode.primaryColor else mode.secondaryColor, 90 + i * 18)
            val start = phase * (if (i % 2 == 0) 1f else -0.7f) + i * 37f
            val sweep = 95f + (uiState.audioLevel * 80f) + i * 8f
            canvas.drawArc(arcBounds, start, sweep, false, paint)
            canvas.drawArc(arcBounds, start + 180f, sweep * 0.45f, false, paint)
        }

        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(34f + uiState.audioLevel * 30f, BlurMaskFilter.Blur.NORMAL)
        paint.color = withAlpha(mode.primaryColor, 130)
        canvas.drawCircle(cx, cy, radius * (0.17f + uiState.audioLevel * 0.05f), paint)
        paint.maskFilter = null

        paint.shader = RadialGradient(cx, cy, radius * 0.24f, mode.secondaryColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 0.24f, paint)
        paint.shader = null

        drawTargetingMarks(canvas, cx, cy, radius)
    }

    private fun drawTargetingMarks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(uiState.mode.secondaryColor, 190)
        val mark = radius * 0.11f
        val gap = radius * 1.12f
        canvas.drawLine(cx - gap, cy, cx - gap + mark, cy, paint)
        canvas.drawLine(cx + gap, cy, cx + gap - mark, cy, paint)
        canvas.drawLine(cx, cy - gap, cx, cy - gap + mark, paint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap - mark, paint)
    }

    private fun drawVoiceWave(canvas: Canvas, cx: Float, y: Float, totalWidth: Float) {
        val bars = 34
        val barW = totalWidth / bars
        paint.style = Paint.Style.FILL
        for (i in 0 until bars) {
            val wave = (sin((phase * 0.045f + i * 0.55f)) + 1f) * 0.5f
            val amp = 8f + wave * 28f + uiState.audioLevel * 50f
            val x = cx - totalWidth / 2f + i * barW
            paint.color = withAlpha(if (i % 3 == 0) uiState.mode.secondaryColor else uiState.mode.primaryColor, 160)
            canvas.drawRoundRect(x, y - amp / 2f, x + barW * 0.42f, y + amp / 2f, 8f, 8f, paint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 13f
        textPaint.color = withAlpha(uiState.mode.primaryColor, 210)
        canvas.drawText(uiState.transcript.take(54), cx, y + 58f, textPaint)
    }

    private fun drawTelemetryPanels(canvas: Canvas) {
        val margin = 22f
        val panelW = width * 0.42f
        val panelH = 112f
        drawPanel(canvas, margin, height * 0.11f, panelW, panelH, "CORE", listOf(
            "MODE ${uiState.mode.label}",
            "UP ${uiState.telemetry.uptimeLabel}",
            "CMD ${uiState.telemetry.commandCount}"
        ))
        drawPanel(canvas, width - margin - panelW, height * 0.11f, panelW, panelH, "DEVICE", listOf(
            "BAT ${if (uiState.telemetry.batteryPercent >= 0) "${uiState.telemetry.batteryPercent}%" else "?"}",
            "NET ${uiState.telemetry.network}",
            "MIC ${if (uiState.telemetry.microphoneReady) "READY" else "STBY"}"
        ))
        drawPanel(canvas, margin, height * 0.70f, panelW, panelH, "MEMORY", listOf(
            "RAM ${uiState.telemetry.memoryUsedMb}/${uiState.telemetry.memoryMaxMb}",
            "STORE %.1fGB".format(uiState.telemetry.storageFreeGb),
            "AUTH ${uiState.telemetry.permissionStatus}"
        ))
        drawPanel(canvas, width - margin - panelW, height * 0.70f, panelW, panelH, "EVENTS", uiState.eventLog.ifEmpty { listOf("NO RECENT EVENTS") }.take(3))
    }

    private fun drawPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, lines: List<String>) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = withAlpha(uiState.mode.primaryColor, 120)
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.shader = LinearGradient(x, y, x + w, y + h, withAlpha(uiState.mode.primaryColor, 32), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.shader = null

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        textPaint.color = withAlpha(uiState.mode.secondaryColor, 235)
        canvas.drawText("[$title]", x + 14f, y + 24f, textPaint)
        textPaint.textSize = 12f
        textPaint.color = withAlpha(Color.WHITE, 200)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line.take(25), x + 14f, y + 49f + index * 20f, textPaint)
        }
    }

    private fun drawCommandStrip(canvas: Canvas) {
        val y = height - 92f
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.BLACK, 120)
        canvas.drawRoundRect(24f, y, width - 24f, y + 58f, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = withAlpha(uiState.mode.primaryColor, 150)
        canvas.drawRoundRect(24f, y, width - 24f, y + 58f, 18f, 18f, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        textPaint.color = withAlpha(uiState.mode.primaryColor, 235)
        canvas.drawText("RESPONSE", 42f, y + 22f, textPaint)
        textPaint.color = withAlpha(Color.WHITE, 215)
        canvas.drawText(uiState.lastResponse.take(64), 42f, y + 44f, textPaint)
    }

    private fun drawStatusHeader(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 18f
        textPaint.color = withAlpha(uiState.mode.secondaryColor, 235)
        canvas.drawText("JARVIS // ADVANCED CIVILIZATION INTERFACE", width / 2f, 42f, textPaint)
        textPaint.textSize = 12f
        textPaint.color = withAlpha(uiState.mode.primaryColor, 210)
        canvas.drawText(uiState.statusLine, width / 2f, 65f, textPaint)
    }

    private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, value: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(uiState.mode.primaryColor, 160)
        canvas.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, paint)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(uiState.mode.secondaryColor, 210)
        canvas.drawRoundRect(x + 3f, y + 3f, x + 3f + (w - 6f) * value, y + h - 3f, h / 2, h / 2, paint)
    }

    private fun percent(value: Float): String = "${(value * 100).toInt()}%"

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
