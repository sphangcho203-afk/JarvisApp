package com.seongja.jarvis

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
        textSize = 22f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val rect = RectF()
    private val path = Path()
    private val particles = List(96) {
        Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2.2f + 0.3f, Random.nextFloat() * 360f)
    }

    private var lastResponse = "Phase 5 local brain awaiting speech stream."
    private var transcript = ""
    private var intent = "idle"
    private var confidence = 0f
    private var memory = "OPERATOR Seongja // FACTS 3 // HISTORY 0"
    private var trace = listOf("phase5_boot", "local_brain_online")
    private var thoughts = listOf("Cognitive lattice initialized.", "Awaiting operator input.")
    private var entities = listOf("operator=Seongja")
    private var decision = "standby"
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
        if (processing) mode = BrainMode.THINKING else if (mode == BrainMode.THINKING) mode = BrainMode.ONLINE
        invalidate()
    }

    fun setTranscript(value: String) {
        transcript = value.take(130)
        invalidate()
    }

    fun submitBrainResponse(response: BrainResponse) {
        commandCount++
        lastResponse = response.display
        intent = response.intent
        confidence = response.confidence
        mode = response.mode
        trace = response.trace.takeLast(9)
        thoughts = response.thoughts.takeLast(5)
        entities = response.entities.takeLast(5)
        decision = response.decision
        memory = response.memory
        pushEvent("BRAIN -> ${response.intent.uppercase()} ${(response.confidence * 100).toInt()}%")
        invalidate()
    }

    fun pushEvent(event: String) {
        events.add(0, event.take(62))
        while (events.size > 9) events.removeLast()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = (SystemClock.uptimeMillis() - bootStart) / 1000f
        drawBackdrop(canvas, t)
        drawCivilizationGrid(canvas, t)
        drawParticleField(canvas, t)
        drawHeader(canvas, t)
        drawPanels(canvas, t)
        drawBrainCore(canvas, t)
        drawThoughtLattice(canvas, t)
        drawVoiceWave(canvas, t)
        drawResponseDock(canvas, t)
        drawBootOverlay(canvas, t)
    }

    private fun drawBackdrop(canvas: Canvas, t: Float) {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(2, 6, 12), Color.rgb(7, 17, 25), Color.rgb(15, 8, 28)),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(26, 0, 255, 230)
        canvas.drawRect(0f, ((t * 95f) % (height + 160)) - 80f, width.toFloat(), ((t * 95f) % (height + 160)) - 77f, paint)
    }

    private fun drawCivilizationGrid(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f
        val gap = 52f
        val skew = 0.38f
        paint.color = Color.argb(44, 0, 245, 255)
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
        paint.color = Color.argb(34, 140, 60, 255)
        var hx = -80f
        while (hx < width + 80f) {
            var hy = -80f
            while (hy < height + 80f) {
                drawHex(canvas, hx, hy, 17f + ((hx + hy + t) % 5f), paint)
                hy += 110f
            }
            hx += 110f
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawParticleField(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        particles.forEachIndexed { index, p ->
            val drift = (t * p.speed * 18f + p.phase) % 360f
            val x = ((p.x * width) + sin(drift.toRad()) * 30f + width) % width
            val y = ((p.y * height) + t * p.speed * 10f + height) % height
            val pulse = (sin((t * 2.3f + index).toDouble()).toFloat() + 1f) * 0.5f
            val purple = if (index % 5 == 0) 150 else 0
            paint.color = Color.argb((42 + pulse * 100).toInt(), purple, 245, 255)
            canvas.drawCircle(x, y, 1.2f + pulse * 2.1f, paint)
        }
    }

    private fun drawHeader(canvas: Canvas, t: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 23f
        textPaint.color = cyan
        canvas.drawText("JARVIS // PHASE 7 LOCAL BRIDGE", width / 2f, 42f, textPaint)
        textPaint.textSize = 12.5f
        textPaint.color = Color.argb(210, 185, 255, 245)
        val voice = when (voiceState) {
            VoiceLoop.State.LISTENING -> "DIRECT LISTENING"
            VoiceLoop.State.PROCESSING -> "VOICE PARSING"
            VoiceLoop.State.ERROR -> "VOICE RECALIBRATING"
            VoiceLoop.State.UNAVAILABLE -> "VOICE UNAVAILABLE"
            VoiceLoop.State.READY -> "VOICE READY"
        }
        canvas.drawText("MODE ${mode.name} // $voice // COMMANDS $commandCount // NO TOUCH REQUIRED", width / 2f, 65f, textPaint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(120, 0, 250, 255)
        canvas.drawLine(width * .17f, 83f, width * .83f, 83f, paint)
    }

    private fun drawPanels(canvas: Canvas, t: Float) {
        val pad = 24f
        val panelW = width * .43f
        val panelH = 136f
        panel(canvas, pad, 145f, panelW, panelH, "[BRAIN]", listOf(
            "INTENT ${intent.take(18)}",
            "CONF ${(confidence * 100).toInt()}%",
            "DEC ${decision.take(20)}",
            "MODE ${mode.name}"
        ))
        panel(canvas, width - pad - panelW, 145f, panelW, panelH, "[OPERATOR]", listOf(
            memory.take(34),
            "VOICE ${voiceState.name}",
            "BAT ${battery()}%",
            "AUTH GRANTED"
        ))
        panel(canvas, pad, height - 445f, panelW, 156f, "[THOUGHTS]", thoughts.map { "• ${it.take(30)}" })
        panel(canvas, width - pad - panelW, height - 445f, panelW, 156f, "[ENTITIES]", entities.map { "• ${it.take(30)}" })
        panel(canvas, pad, height - 260f, width - pad * 2f, 86f, "[TRACE BUS]", trace.takeLast(3).map { "→ ${it.take(58)}" })
    }

    private fun panel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, lines: List<String>) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(170, 0, 245, 255)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(36, 0, 255, 230)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 14f
        textPaint.color = cyan
        canvas.drawText(title, x + 16f, y + 27f, textPaint)
        textPaint.textSize = 11.6f
        textPaint.color = Color.argb(215, 218, 255, 248)
        lines.take(5).forEachIndexed { i, line -> canvas.drawText(line, x + 16f, y + 51f + i * 17f, textPaint) }
    }

    private fun drawBrainCore(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f + 10f
        val base = min(width, height) * .225f
        val c = modeColor()
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, base * .95f, c, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, base * .95f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 12) {
            val r = base * (.42f + i * .105f)
            val start = (t * (18 + i * 2) + i * 29) % 360f
            val sweep = 30f + (i % 4) * 18f
            paint.strokeWidth = if (i % 3 == 0) 4.5f else 2.0f
            paint.color = if (i % 4 == 0) Color.argb(190, 170, 90, 255) else Color.argb(175, Color.red(c), Color.green(c), Color.blue(c))
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, start, sweep, false, paint)
        }

        paint.strokeWidth = 1.4f
        paint.color = Color.argb(120, 210, 255, 245)
        for (i in 0 until 16) {
            val a = (i * 22.5f + t * 16f).toRad()
            val r1 = base * 1.1f
            val r2 = base * 1.32f
            canvas.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, paint)
        }

        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
        paint.color = c
        canvas.drawCircle(cx, cy, base * .20f + pulse(t, 9f) * base * .06f, paint)
        paint.maskFilter = null
        paint.color = Color.argb(230, Color.red(c), Color.green(c), Color.blue(c))
        canvas.drawCircle(cx, cy, base * .12f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 12f
        textPaint.color = Color.argb(210, 220, 255, 250)
        canvas.drawText("LOCAL CORTEX", cx, cy + base * 1.55f, textPaint)
    }

    private fun drawThoughtLattice(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f + 10f
        val radius = min(width, height) * .36f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f
        paint.color = Color.argb(70, 170, 110, 255)
        for (i in 0 until 7) {
            val a1 = (i * 51f + t * 10f).toRad()
            val a2 = ((i + 2) * 51f - t * 8f).toRad()
            canvas.drawLine(cx + cos(a1) * radius, cy + sin(a1) * radius, cx + cos(a2) * radius * .75f, cy + sin(a2) * radius * .75f, paint)
        }
    }

    private fun drawVoiceWave(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val y = height / 2f + min(width, height) * .285f
        val bars = 52
        val spacing = width * .016f
        paint.style = Paint.Style.FILL
        for (i in 0 until bars) {
            val amp = abs(sin((t * 3.1f + i * .54f).toDouble())).toFloat()
            val boost = if (voiceState == VoiceLoop.State.LISTENING) 1f else .38f
            val h = 6f + amp * 58f * boost
            val x = cx + (i - bars / 2f) * spacing
            paint.color = Color.argb(135 + (amp * 100).toInt(), if (i % 4 == 0) 150 else 0, 245, 255)
            rect.set(x - 3.0f, y - h / 2f, x + 3.0f, y + h / 2f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 12.5f
        textPaint.color = Color.argb(210, 180, 255, 245)
        canvas.drawText(transcript.ifBlank { "direct listening stream active" }.take(52), cx, y + 78f, textPaint)
    }

    private fun drawResponseDock(canvas: Canvas, t: Float) {
        val h = 128f
        val top = height - h - 28f
        rect.set(24f, top, width - 24f, height - 28f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(165, 0, 225, 255)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(54, 0, 24, 42)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        textPaint.color = cyan
        canvas.drawText("BRAIN RESPONSE // ${intent.uppercase().take(28)}", 44f, top + 27f, textPaint)
        textPaint.textSize = 11.7f
        textPaint.color = Color.argb(220, 220, 255, 248)
        wrap(lastResponse, 88).take(4).forEachIndexed { i, line -> canvas.drawText(line, 44f, top + 52f + i * 17f, textPaint) }
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = Color.argb(175, 0, 255, 220)
        canvas.drawText(events.firstOrNull().orEmpty().take(36), width - 44f, top + 27f, textPaint)
    }

    private fun drawBootOverlay(canvas: Canvas, t: Float) {
        if (t > 3.0f) return
        val alpha = ((1f - t / 3.0f).coerceIn(0f, 1f) * 220).toInt()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 2, 4, 12)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.color = Color.argb(alpha, 0, 255, 235)
        canvas.drawText("OFFLINE CORTEX BOOTING", width / 2f, height / 2f - 42f, textPaint)
        textPaint.textSize = 13.5f
        val pct = ((t / 3.0f) * 100f).toInt().coerceAtMost(100)
        canvas.drawText("INTENT MATRIX $pct% // MEMORY VAULT LINKED", width / 2f, height / 2f, textPaint)
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
        BrainMode.ALERT -> Color.rgb(255, 60, 82)
        BrainMode.STEALTH -> Color.rgb(105, 125, 255)
        BrainMode.TACTICAL -> Color.rgb(255, 205, 82)
        BrainMode.LEARNING -> Color.rgb(160, 255, 120)
        BrainMode.EXECUTING -> Color.rgb(255, 165, 80)
        BrainMode.SECURITY -> Color.rgb(255, 95, 220)
        BrainMode.THINKING -> Color.rgb(135, 255, 180)
        else -> cyan
    }

    private fun battery(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: 0
    }

    private fun wrap(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        var left = text
        while (left.length > max && out.size < 5) {
            val cut = left.take(max).lastIndexOf(' ').takeIf { it > 24 } ?: max
            out += left.take(cut)
            left = left.drop(cut).trim()
        }
        if (left.isNotBlank()) out += left
        return out
    }

    private fun pulse(t: Float, speed: Float): Float = ((sin((t * speed).toDouble()).toFloat() + 1f) * .5f)
    private fun Float.toRad(): Float = (this * PI / 180.0).toFloat()

    private data class Particle(var x: Float, var y: Float, var speed: Float, var phase: Float)

    companion object {
        private val cyan = Color.rgb(0, 245, 235)
        private val events = mutableListOf("PHASE 7 -> HUD READY")
    }
}
