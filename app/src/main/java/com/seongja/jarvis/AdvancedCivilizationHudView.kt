package com.seongja.jarvis

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class AdvancedCivilizationHudView(context: Context) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val bootStart = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        textSize = 24f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val rect = RectF()
    private val path = Path()
    private val particles = List(72) {
        Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.8f + 0.4f, Random.nextFloat() * 360f)
    }

    private var lastResponse = "Awaiting cognition stream."
    private var transcript = ""
    private var intent = "none"
    private var confidence = 0f
    private var memory = "CALLSIGN Sir // NOTES 0"
    private var trace = listOf("boot_sequence", "visual_core_online")
    private var voiceState = VoiceLoop.State.READY
    private var mode = BrainMode.ONLINE
    private var commandCount = 0

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        handler.post(object : Runnable {
            override fun run() {
                invalidate()
                handler.postDelayed(this, 16L)
            }
        })
    }

    fun setVoiceState(state: VoiceLoop.State) {
        voiceState = state
        if (state == VoiceLoop.State.LISTENING) mode = BrainMode.LISTENING
        invalidate()
    }

    fun setProcessing(processing: Boolean) {
        if (processing) mode = BrainMode.PROCESSING else if (mode == BrainMode.PROCESSING) mode = BrainMode.ONLINE
        invalidate()
    }

    fun setTranscript(value: String) {
        transcript = value.take(120)
        invalidate()
    }

    fun submitBrainResponse(response: BrainResponse) {
        commandCount++
        lastResponse = response.display
        intent = response.intent
        confidence = response.confidence
        mode = response.mode
        trace = response.trace.takeLast(8)
        memory = response.memory
        pushEvent("BRAIN -> ${response.intent.uppercase()} ${(response.confidence * 100).toInt()}%")
        invalidate()
    }

    fun pushEvent(event: String) {
        events.add(0, event.take(58))
        while (events.size > 8) events.removeLast()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = (SystemClock.uptimeMillis() - bootStart) / 1000f
        drawBackdrop(canvas, t)
        drawPerspectiveGrid(canvas, t)
        drawParticleField(canvas, t)
        drawTopHeader(canvas, t)
        drawTelemetryPanels(canvas, t)
        drawReactorCore(canvas, t)
        drawVoiceWave(canvas, t)
        drawNeuralTrace(canvas, t)
        drawBottomDock(canvas, t)
        drawBootOverlay(canvas, t)
    }

    private fun drawBackdrop(canvas: Canvas, t: Float) {
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(5, 10, 15), Color.rgb(13, 28, 31), Color.rgb(4, 8, 16)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        paint.color = Color.argb(34, 0, 255, 230)
        val scanY = ((t * 90f) % (height + 200)) - 100f
        canvas.drawRect(0f, scanY, width.toFloat(), scanY + 2f, paint)
        paint.color = Color.argb(20, 0, 180, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawPerspectiveGrid(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(54, 0, 245, 255)
        val gap = 58f
        val skew = 0.28f
        var x = -width.toFloat()
        while (x < width * 2f) {
            canvas.drawLine(x + (t * 7f % gap), 0f, x + width * skew + (t * 7f % gap), height.toFloat(), paint)
            x += gap
        }
        var y = -height.toFloat()
        while (y < height * 2f) {
            canvas.drawLine(0f, y + (t * 5f % gap), width.toFloat(), y - width * skew + (t * 5f % gap), paint)
            y += gap
        }

        paint.color = Color.argb(28, 0, 255, 170)
        val hexGap = 96f
        var hx = -hexGap
        while (hx < width + hexGap) {
            var hy = -hexGap
            while (hy < height + hexGap) {
                drawHex(canvas, hx, hy, 14f + ((hx + hy + t) % 6f), paint)
                hy += hexGap
            }
            hx += hexGap
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawParticleField(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        particles.forEachIndexed { index, p ->
            val drift = (t * p.speed * 18f + p.phase) % 360f
            val x = ((p.x * width) + sin(drift.toRad()) * 22f + width) % width
            val y = ((p.y * height) + t * p.speed * 8f + height) % height
            val pulse = (sin((t * 2f + index).toDouble()).toFloat() + 1f) * 0.5f
            paint.color = Color.argb((45 + pulse * 95).toInt(), 0, 255, 235)
            canvas.drawCircle(x, y, 1.4f + pulse * 1.8f, paint)
        }
    }

    private fun drawTopHeader(canvas: Canvas, t: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.color = cyan
        canvas.drawText("JARVIS // COGNITIVE CIVILIZATION CORE", width / 2f, 44f, textPaint)
        textPaint.textSize = 13f
        textPaint.color = Color.argb(190, 110, 255, 240)
        val voice = when (voiceState) {
            VoiceLoop.State.LISTENING -> "STREAM LISTENING"
            VoiceLoop.State.PROCESSING -> "VOICE PROCESSING"
            VoiceLoop.State.ERROR -> "VOICE RECALIBRATING"
            VoiceLoop.State.UNAVAILABLE -> "VOICE UNAVAILABLE"
            VoiceLoop.State.READY -> "VOICE READY"
        }
        canvas.drawText("CORE ${mode.name} // $voice // NO TOUCH REQUIRED", width / 2f, 68f, textPaint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(120, 0, 250, 255)
        canvas.drawLine(width * .22f, 84f, width * .78f, 84f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawTelemetryPanels(canvas: Canvas, t: Float) {
        val pad = 28f
        val panelW = width * 0.42f
        val panelH = 126f
        val top = 170f
        val bottom = height - 330f

        panel(canvas, pad, top, panelW, panelH, "[CORE]", listOf(
            "MODE ${mode.name}",
            "VOICE ${voiceState.name}",
            "CYCLE $commandCount",
            "INTENT ${intent.take(18)}"
        ))
        panel(canvas, width - pad - panelW, top, panelW, panelH, "[DEVICE]", listOf(
            "BAT ${battery()}%",
            "NET CELLULAR",
            "MIC ${voiceState.name}",
            "AUTH GRANTED"
        ))
        panel(canvas, pad, bottom, panelW, panelH, "[MEMORY]", listOf(
            memory.take(32),
            "TRACE ${trace.size} NODES",
            "CONF ${(confidence * 100).toInt()}%",
            "VAULT LOCAL"
        ))
        panel(canvas, width - pad - panelW, bottom, panelW, panelH, "[COGNITION]", trace.takeLast(4).map { "• ${it.take(26)}" })
    }

    private fun panel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, lines: List<String>) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.argb(160, 0, 245, 255)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        paint.color = Color.argb(32, 0, 255, 230)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 16f, 16f, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 15f
        textPaint.color = cyan
        canvas.drawText(title, x + 18f, y + 27f, textPaint)
        textPaint.textSize = 12.5f
        textPaint.color = Color.argb(205, 205, 255, 248)
        lines.take(4).forEachIndexed { i, line ->
            canvas.drawText(line, x + 18f, y + 52f + i * 18f, textPaint)
        }
    }

    private fun drawReactorCore(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f + 18f
        val base = min(width, height) * 0.21f
        val modeColor = modeColor()

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, base * .75f, modeColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, base * .75f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 9) {
            val r = base * (0.35f + i * 0.105f)
            val start = (t * (22 + i * 9) + i * 38) % 360f
            val sweep = 42f + (sin((t + i).toDouble()).toFloat() + 1f) * 28f
            paint.strokeWidth = if (i % 2 == 0) 4f else 2f
            paint.color = Color.argb(140 + (i * 8).coerceAtMost(80), Color.red(modeColor), Color.green(modeColor), Color.blue(modeColor))
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, start, sweep, false, paint)
            canvas.drawArc(rect, start + 180f, sweep * .7f, false, paint)
        }

        // Targeting crosshair
        paint.strokeWidth = 2f
        paint.color = Color.argb(155, 140, 255, 235)
        canvas.drawLine(cx - base * 1.45f, cy, cx - base * 1.18f, cy, paint)
        canvas.drawLine(cx + base * 1.18f, cy, cx + base * 1.45f, cy, paint)
        canvas.drawLine(cx, cy - base * 1.45f, cx, cy - base * 1.18f, paint)
        canvas.drawLine(cx, cy + base * 1.18f, cx, cy + base * 1.45f, paint)

        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        paint.color = modeColor
        canvas.drawCircle(cx, cy, base * .18f + pulse(t, 11f) * base * .05f, paint)
        paint.maskFilter = null
        paint.color = Color.argb(220, Color.red(modeColor), Color.green(modeColor), Color.blue(modeColor))
        canvas.drawCircle(cx, cy, base * .11f, paint)
    }

    private fun drawVoiceWave(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f + min(width, height) * .24f
        val bars = 44
        val spacing = width * .018f
        val maxH = 52f
        paint.style = Paint.Style.FILL
        for (i in 0 until bars) {
            val x = cx + (i - bars / 2f) * spacing
            val amp = abs(sin((t * 3.2f + i * .62f).toDouble())).toFloat()
            val activity = if (voiceState == VoiceLoop.State.LISTENING) 1f else .45f
            val h = 6f + amp * maxH * activity
            paint.color = Color.argb(145 + (amp * 90).toInt(), 0, 245, 255)
            rect.set(x - 3.2f, cy - h / 2f, x + 3.2f, cy + h / 2f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 13f
        textPaint.color = Color.argb(190, 125, 255, 245)
        canvas.drawText(transcript.ifBlank { "listening stream active" }.take(42), cx, cy + 76f, textPaint)
    }

    private fun drawNeuralTrace(canvas: Canvas, t: Float) {
        val left = width * .15f
        val right = width * .85f
        val y = height * .69f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.color = Color.argb(75, 0, 255, 230)
        path.reset()
        path.moveTo(left, y)
        for (i in 0..12) {
            val x = left + (right - left) * i / 12f
            val yy = y + sin((i * .8f + t * 1.7f).toDouble()).toFloat() * 22f
            path.lineTo(x, yy)
        }
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        for (i in 0..12) {
            val x = left + (right - left) * i / 12f
            val yy = y + sin((i * .8f + t * 1.7f).toDouble()).toFloat() * 22f
            paint.color = Color.argb(90 + ((i * 13) % 100), 0, 255, 230)
            canvas.drawCircle(x, yy, 3.5f + pulse(t + i, 3f) * 2f, paint)
        }
    }

    private fun drawBottomDock(canvas: Canvas, t: Float) {
        val dockH = 104f
        val top = height - dockH - 28f
        rect.set(26f, top, width - 26f, height - 30f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(150, 0, 225, 255)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(42, 0, 35, 48)
        canvas.drawRoundRect(rect, 18f, 18f, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        textPaint.color = cyan
        canvas.drawText("RESPONSE // ${intent.uppercase().take(26)}", 46f, top + 28f, textPaint)
        textPaint.textSize = 12f
        textPaint.color = Color.argb(210, 215, 255, 248)
        val lines = lastResponse.lines().flatMap { wrap(it, 82) }.take(3)
        lines.forEachIndexed { i, line -> canvas.drawText(line, 46f, top + 52f + i * 17f, textPaint) }

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = Color.argb(170, 0, 255, 220)
        canvas.drawText("${events.firstOrNull().orEmpty().take(32)}", width - 46f, top + 28f, textPaint)
    }

    private fun drawBootOverlay(canvas: Canvas, t: Float) {
        if (t > 3.4f) return
        val alpha = ((1f - (t / 3.4f)).coerceIn(0f, 1f) * 210).toInt()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 0, 10, 14)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.color = Color.argb(alpha, 0, 255, 235)
        canvas.drawText("INITIALIZING COGNITIVE LATTICE", width / 2f, height / 2f - 42f, textPaint)
        textPaint.textSize = 14f
        val pct = ((t / 3.4f) * 100).coerceAtMost(100f).toInt()
        canvas.drawText("BOOT SEQUENCE $pct% // NEURAL BUS ONLINE", width / 2f, height / 2f, textPaint)
    }

    private fun drawHex(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        path.reset()
        for (i in 0..6) {
            val a = (PI / 3.0 * i + PI / 6.0).toFloat()
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, p)
    }

    private fun modeColor(): Int = when (mode) {
        BrainMode.ALERT -> Color.rgb(255, 67, 82)
        BrainMode.STEALTH -> Color.rgb(92, 125, 255)
        BrainMode.TACTICAL -> Color.rgb(255, 194, 87)
        BrainMode.PROCESSING, BrainMode.THINKING -> Color.rgb(170, 255, 120)
        BrainMode.SECURITY -> Color.rgb(255, 110, 210)
        else -> cyan
    }

    private fun battery(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: 0
    }

    private fun wrap(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > max && result.size < 4) {
            val cut = remaining.take(max).lastIndexOf(' ').takeIf { it > 20 } ?: max
            result += remaining.take(cut)
            remaining = remaining.drop(cut).trim()
        }
        if (remaining.isNotBlank()) result += remaining
        return result
    }

    private fun pulse(t: Float, speed: Float): Float = ((sin((t * speed).toDouble()).toFloat() + 1f) * .5f)
    private fun Float.toRad(): Float = (this * PI / 180.0).toFloat()

    private data class Particle(var x: Float, var y: Float, var speed: Float, var phase: Float)

    companion object {
        private val cyan = Color.rgb(0, 245, 235)
        private val events = mutableListOf("SYSTEM -> HUD READY")
    }
}
