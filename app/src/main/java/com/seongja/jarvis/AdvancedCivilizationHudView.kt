package com.seongja.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.jarvis.core.device.DeviceTelemetry
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class AdvancedCivilizationHudView(context: Context) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val telemetry = DeviceTelemetry(context.applicationContext)
    private val bootStart = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val rect = RectF()
    private val path = Path()
    private val particles = List(110) {
        Particle(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            speed = Random.nextFloat() * 1.9f + 0.28f,
            phase = Random.nextFloat() * 360f,
            size = Random.nextFloat() * 1.8f + 0.5f
        )
    }
    private val events = mutableListOf("PHASE 9.2A -> ACTION FABRIC READY")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    private var lastResponse = "Neural command system standing by. Speak naturally."
    private var lastResponseUpdatedAtMs = SystemClock.uptimeMillis()
    private var transcript = ""
    private var intent = "idle"
    private var confidence = 0f
    private var memory = "OPERATOR SEONGJA // LOCAL MEMORY ONLINE"
    private var trace = listOf("phase91_boot", "execution_kernel_ready", "voice_loop_ready")
    private var thoughts = listOf("Cognitive lattice initialized.", "Awaiting operator input.")
    private var entities = listOf("operator=Seongja")
    private var decision = "standby"
    private var voiceState = VoiceLoop.State.READY
    private var mode = BrainMode.BOOT
    private var commandCount = 0
    private var targetVoiceEnergy = 0f
    private var displayVoiceEnergy = 0f

    private var countdownActive = false
    private var countdownLabel = "MISSION TIMER"
    private var countdownRemainingMs = 0L
    private var countdownTotalMs = 0L
    private var countdownProgress = 0f

    private var cachedTime = "00:00:00"
    private var cachedBattery = 0
    private var cachedNetwork = "OFFLINE"
    private var cachedHeapMb = 0L
    private var lastTelemetryRefresh = 0L

    private val animator = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    init {
        isFocusable = true
        isClickable = true
        handler.post(animator)
    }

    fun setVoiceState(state: VoiceLoop.State) {
        voiceState = state
        mode = when (state) {
            VoiceLoop.State.LISTENING -> BrainMode.LISTENING
            VoiceLoop.State.PROCESSING -> BrainMode.THINKING
            VoiceLoop.State.ERROR -> BrainMode.ALERT
            VoiceLoop.State.UNAVAILABLE -> BrainMode.SECURITY
            VoiceLoop.State.READY -> if (mode == BrainMode.BOOT || mode == BrainMode.LISTENING || mode == BrainMode.THINKING) BrainMode.ONLINE else mode
        }
        invalidate()
    }

    fun setVoiceAmplitude(value: Float) {
        targetVoiceEnergy = value.coerceIn(0f, 1f)
    }

    fun setProcessing(processing: Boolean) {
        mode = if (processing) BrainMode.THINKING else if (mode == BrainMode.THINKING) BrainMode.ONLINE else mode
        invalidate()
    }

    fun setTranscript(value: String) {
        transcript = value.take(MAX_STREAM_TEXT)
        invalidate()
    }

    internal fun setCountdown(snapshot: CountdownSnapshot) {
        countdownActive = snapshot.active
        countdownLabel = snapshot.label
        countdownRemainingMs = snapshot.remainingMs
        countdownTotalMs = snapshot.totalMs
        countdownProgress = snapshot.progress
        invalidate()
    }

    fun submitBrainResponse(response: BrainResponse) {
        commandCount++
        lastResponse = response.display.take(MAX_STREAM_TEXT)
        lastResponseUpdatedAtMs = SystemClock.uptimeMillis()
        intent = response.intent
        confidence = response.confidence
        mode = response.mode
        trace = response.trace.takeLast(8)
        thoughts = response.thoughts.takeLast(4)
        entities = response.entities.takeLast(4)
        decision = response.decision
        memory = response.memory
        pushEvent("BRAIN -> ${response.intent.uppercase(Locale.US)} ${(response.confidence * 100).toInt()}%")
        invalidate()
    }

    fun pushEvent(event: String) {
        events.add(0, event.take(74))
        while (events.size > 10) events.removeLast()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = (SystemClock.uptimeMillis() - bootStart) / 1000f
        refreshTelemetry()
        updateAnimationState()

        drawBackdrop(canvas, t)
        drawPerspectiveGrid(canvas, t)
        drawParticleField(canvas, t)
        drawFrameGeometry(canvas, t)
        drawHeader(canvas, t)
        drawSidePanels(canvas, t)
        drawNeuralCore(canvas, t)
        drawVoiceArray(canvas, t)
        drawCommandDock(canvas, t)
        drawEventRail(canvas)
        drawBootOverlay(canvas, t)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun refreshTelemetry() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTelemetryRefresh < 1_000L) return
        lastTelemetryRefresh = now
        cachedTime = LocalTime.now().format(timeFormatter)
        cachedBattery = telemetry.battery().percent
        cachedNetwork = telemetry.network().label
        val runtime = Runtime.getRuntime()
        cachedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
    }

    private fun updateAnimationState() {
        val desired = if (voiceState == VoiceLoop.State.LISTENING) targetVoiceEnergy else 0f
        val factor = if (desired > displayVoiceEnergy) 0.32f else 0.11f
        displayVoiceEnergy += (desired - displayVoiceEnergy) * factor
        if (displayVoiceEnergy < 0.005f) displayVoiceEnergy = 0f
    }

    private fun drawBackdrop(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(1, 5, 11),
                Color.rgb(3, 14, 23),
                Color.rgb(8, 8, 24),
                Color.rgb(2, 5, 10)
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val scanY = ((t * dp(76f)) % (height + dp(180f))) - dp(90f)
        paint.color = Color.argb(22, 0, 255, 235)
        canvas.drawRect(0f, scanY, width.toFloat(), scanY + dp(2.2f), paint)
        paint.shader = LinearGradient(
            0f,
            scanY - dp(28f),
            0f,
            scanY + dp(28f),
            intArrayOf(Color.TRANSPARENT, Color.argb(12, 0, 245, 255), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, scanY - dp(28f), width.toFloat(), scanY + dp(28f), paint)
        paint.shader = null
    }

    private fun drawPerspectiveGrid(canvas: Canvas, t: Float) {
        val horizon = height * 0.62f
        val centerX = width / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.45f)
        paint.color = Color.argb(40, 20, 224, 255)

        val rayCount = 24
        for (i in 0..rayCount) {
            val bottomX = width * (i / rayCount.toFloat())
            val topX = centerX + (bottomX - centerX) * 0.13f
            canvas.drawLine(topX, horizon, bottomX, height.toFloat(), paint)
        }

        val phase = (t * 0.18f) % 1f
        for (i in 0 until 18) {
            val p = ((i + phase) / 18f).coerceIn(0f, 1f)
            val eased = p * p
            val y = horizon + eased * (height - horizon)
            val inset = (1f - eased) * width * 0.43f
            canvas.drawLine(inset, y, width - inset, y, paint)
        }

        paint.color = Color.argb(25, 127, 82, 255)
        paint.strokeWidth = dp(0.5f)
        val hexRadius = dp(18f)
        var y = height * 0.08f
        while (y < height * 0.62f) {
            var x = -hexRadius
            val row = (y / (hexRadius * 1.7f)).toInt()
            if (row % 2 != 0) x += hexRadius * 0.9f
            while (x < width + hexRadius) {
                drawHex(canvas, x, y, hexRadius, paint)
                x += hexRadius * 3.1f
            }
            y += hexRadius * 2.7f
        }
    }

    private fun drawParticleField(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        particles.forEachIndexed { index, particle ->
            val drift = (t * particle.speed * 15f + particle.phase) % 360f
            val x = ((particle.x * width) + sin(drift.toRad()) * dp(18f) + width) % width
            val y = ((particle.y * height) + t * particle.speed * dp(5f) + height) % height
            val pulse = ((sin((t * 2.1f + index * 0.41f).toDouble()).toFloat() + 1f) * 0.5f)
            val violet = if (index % 7 == 0) 126 else 0
            paint.color = Color.argb((26 + pulse * 82).toInt(), violet, 234, 255)
            canvas.drawCircle(x, y, dp(particle.size * (0.7f + pulse * 0.6f)), paint)
        }
    }

    private fun drawFrameGeometry(canvas: Canvas, t: Float) {
        val inset = dp(14f)
        val corner = dp(48f)
        val accent = modeColor()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = Color.argb(125, Color.red(accent), Color.green(accent), Color.blue(accent))

        path.reset()
        path.moveTo(inset, inset + corner)
        path.lineTo(inset, inset)
        path.lineTo(inset + corner, inset)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(width - inset - corner, inset)
        path.lineTo(width - inset, inset)
        path.lineTo(width - inset, inset + corner)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(inset, height - inset - corner)
        path.lineTo(inset, height - inset)
        path.lineTo(inset + corner, height - inset)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(width - inset - corner, height - inset)
        path.lineTo(width - inset, height - inset)
        path.lineTo(width - inset, height - inset - corner)
        canvas.drawPath(path, paint)

        val pulseAlpha = (45 + pulse(t, 2.4f) * 75).toInt()
        paint.color = Color.argb(pulseAlpha, 0, 245, 255)
        val marks = 12
        for (i in 0 until marks) {
            val x = width * (0.08f + i * 0.84f / (marks - 1f))
            canvas.drawLine(x, dp(78f), x + dp(9f), dp(78f), paint)
        }
    }

    private fun drawHeader(canvas: Canvas, t: Float) {
        val accent = modeColor()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textSize = sp(16.5f)
        textPaint.color = accent
        textPaint.setShadowLayer(dp(8f), 0f, 0f, Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawText("JARVIS // DISTRIBUTED CORTEX SYSTEM", width / 2f, dp(39f), textPaint)
        textPaint.clearShadowLayer()

        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(9.2f)
        textPaint.color = Color.argb(220, 183, 242, 247)
        canvas.drawText("PHASE 9.1  •  EXECUTION KERNEL  •  TEN-NODE CORTEX", width / 2f, dp(59f), textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(8.8f)
        textPaint.color = Color.argb(210, 128, 214, 225)
        canvas.drawText("TIME $cachedTime  //  LINK $cachedNetwork", dp(24f), dp(84f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BAT $cachedBattery%  //  HEAP ${cachedHeapMb}MB", width - dp(24f), dp(84f), textPaint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.8f)
        paint.color = Color.argb(100, 0, 240, 255)
        canvas.drawLine(dp(24f), dp(94f), width - dp(24f), dp(94f), paint)

        val sweepX = dp(24f) + ((t * dp(85f)) % max(dp(1f), width - dp(48f)))
        paint.strokeWidth = dp(1.8f)
        paint.color = Color.argb(210, 0, 255, 225)
        canvas.drawLine(sweepX, dp(92f), min(width - dp(24f), sweepX + dp(26f)), dp(92f), paint)
    }

    private fun drawSidePanels(canvas: Canvas, t: Float) {
        val panelTop = height * 0.115f
        val panelWidth = width * 0.30f
        val panelHeight = height * 0.164f
        val margin = width * 0.035f

        drawPanel(
            canvas = canvas,
            x = margin,
            y = panelTop,
            w = panelWidth,
            h = panelHeight,
            title = "COGNITION",
            lines = listOf(
                "INTENT ${intent.take(15).uppercase(Locale.US)}",
                "CONF ${(confidence * 100).toInt()}%",
                "DEC ${decision.take(16).uppercase(Locale.US)}",
                "MODE ${mode.name}"
            ),
            pulse = pulse(t, 2.2f)
        )

        drawPanel(
            canvas = canvas,
            x = width - margin - panelWidth,
            y = panelTop,
            w = panelWidth,
            h = panelHeight,
            title = "SYSTEM",
            lines = listOf(
                "VOICE ${voiceLabel()}",
                "RMS ${(displayVoiceEnergy * 100).toInt()}%",
                "CMDS $commandCount",
                "AUTH LOCAL"
            ),
            pulse = pulse(t + 0.8f, 2.2f)
        )

        val lowerY = height * 0.52f
        val lowerHeight = height * 0.105f
        drawPanel(
            canvas,
            margin,
            lowerY,
            panelWidth,
            lowerHeight,
            "THOUGHT BUS",
            thoughts.takeLast(3).map { it.take(25) },
            pulse(t + 1.4f, 1.7f)
        )
        drawPanel(
            canvas,
            width - margin - panelWidth,
            lowerY,
            panelWidth,
            lowerHeight,
            "ENTITY MAP",
            entities.takeLast(3).map { it.take(25) },
            pulse(t + 2.1f, 1.7f)
        )
    }

    private fun drawPanel(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        lines: List<String>,
        pulse: Float
    ) {
        val accent = modeColor()
        rect.set(x, y, x + w, y + h)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            x,
            y,
            x + w,
            y + h,
            intArrayOf(Color.argb(72, 2, 27, 39), Color.argb(28, 25, 8, 45)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, dp(9f), dp(9f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.9f)
        paint.color = Color.argb((105 + pulse * 55).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(rect, dp(9f), dp(9f), paint)

        val cut = dp(13f)
        paint.strokeWidth = dp(1.6f)
        canvas.drawLine(x, y + cut, x + cut, y, paint)
        canvas.drawLine(x + w - cut, y, x + w, y + cut, paint)

        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(8.4f)
        textPaint.color = accent
        canvas.drawText(title, x + dp(10f), y + dp(18f), textPaint)

        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(7.8f)
        textPaint.color = Color.argb(220, 205, 242, 247)
        val lineGap = max(dp(14f), h / 6f)
        lines.take(4).forEachIndexed { index, line ->
            canvas.drawText(line, x + dp(10f), y + dp(38f) + index * lineGap, textPaint)
        }
    }

    private fun drawNeuralCore(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height * 0.37f
        val base = min(width * 0.205f, height * 0.118f)
        val accent = modeColor()
        val energy = 0.18f + displayVoiceEnergy * 0.82f

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx,
            cy,
            base * 1.65f,
            intArrayOf(
                Color.argb((70 + energy * 85).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(28, 0, 220, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, base * 1.65f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 10) {
            val radius = base * (0.52f + i * 0.12f)
            val direction = if (i % 2 == 0) 1f else -1f
            val start = (t * (13f + i * 2.8f) * direction + i * 37f) % 360f
            val sweep = 22f + (i % 4) * 17f + displayVoiceEnergy * 15f
            paint.strokeWidth = dp(if (i % 3 == 0) 2.2f else 1.05f)
            paint.color = if (i % 4 == 0) {
                Color.argb(185, 145, 86, 255)
            } else {
                Color.argb(175, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, start, sweep, false, paint)
            canvas.drawArc(rect, start + 180f, sweep * 0.62f, false, paint)
        }

        drawScannerSweep(canvas, cx, cy, base * 1.45f, t, accent)
        drawOrbitalNodes(canvas, cx, cy, base, t, accent)

        val coreRadius = base * (0.19f + pulse(t, 4.8f) * 0.025f + displayVoiceEnergy * 0.045f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, coreRadius * 2.6f, paint)
        paint.color = Color.argb(62, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, coreRadius * 1.85f, paint)
        paint.color = Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, coreRadius * 1.35f, paint)
        paint.color = accent
        canvas.drawCircle(cx, cy, coreRadius, paint)
        paint.color = Color.argb(230, 235, 255, 255)
        canvas.drawCircle(cx, cy, coreRadius * 0.34f, paint)

        drawCountdownLayer(canvas, cx, cy, base, accent)
    }

    private fun drawScannerSweep(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float, accent: Int) {
        canvas.save()
        canvas.rotate((t * 34f) % 360f, cx, cy)
        paint.style = Paint.Style.FILL
        paint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(Color.TRANSPARENT, Color.argb(8, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT),
            floatArrayOf(0f, 0.76f, 0.96f, 1f)
        )
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 0f, 360f, true, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun drawOrbitalNodes(canvas: Canvas, cx: Float, cy: Float, base: Float, t: Float, accent: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.65f)
        paint.color = Color.argb(78, 180, 235, 255)
        for (i in 0 until 12) {
            val angle = (i * 30f + t * (if (i % 2 == 0) 13f else -9f)).toRad()
            val radius = base * (1.12f + (i % 3) * 0.11f)
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            canvas.drawLine(cx, cy, x, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (i % 4 == 0) Color.rgb(155, 90, 255) else accent
            canvas.drawCircle(x, y, dp(if (i % 3 == 0) 2.5f else 1.35f), paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(78, 180, 235, 255)
        }
    }

    private fun drawCountdownLayer(canvas: Canvas, cx: Float, cy: Float, base: Float, accent: Int) {
        if (countdownTotalMs > 0L) {
            val radius = base * 1.62f
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3.2f)
            paint.color = Color.argb(36, 210, 240, 255)
            canvas.drawArc(rect, -90f, 360f, false, paint)
            paint.shader = SweepGradient(
                cx,
                cy,
                intArrayOf(accent, Color.rgb(155, 90, 255), accent),
                null
            )
            paint.color = Color.WHITE
            canvas.drawArc(rect, -90f, 360f * countdownProgress, false, paint)
            paint.shader = null
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.color = Color.argb(235, 230, 255, 255)
        textPaint.textSize = sp(if (countdownActive) 15.5f else 9.3f)
        val primary = if (countdownActive) formatCountdown((countdownRemainingMs + 999L) / 1_000L) else mode.name
        canvas.drawText(primary, cx, cy + dp(5f), textPaint)

        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(6.9f)
        textPaint.color = Color.argb(190, 150, 230, 238)
        val secondary = if (countdownActive) countdownLabel else voiceLabel()
        canvas.drawText(secondary.take(25), cx, cy + dp(23f), textPaint)
    }

    private fun drawVoiceArray(canvas: Canvas, t: Float) {
        val centerX = width / 2f
        val y = height * 0.655f
        val arrayWidth = width * 0.80f
        val bars = 61
        val spacing = arrayWidth / bars
        val accent = modeColor()

        paint.style = Paint.Style.FILL
        for (i in 0 until bars) {
            val normalizedX = (i - bars / 2f) / (bars / 2f)
            val envelope = (1f - abs(normalizedX)).coerceAtLeast(0.12f)
            val organic = abs(sin((t * 4.2f + i * 0.47f).toDouble())).toFloat()
            val idle = 0.08f + organic * 0.12f
            val active = idle + displayVoiceEnergy * envelope * (0.55f + organic * 0.45f)
            val barHeight = dp(4f) + active * dp(48f)
            val x = centerX + (i - bars / 2f) * spacing
            val alpha = (90 + active * 165).toInt().coerceIn(0, 255)
            paint.color = if (i % 9 == 0) {
                Color.argb(alpha, 155, 82, 255)
            } else {
                Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            rect.set(x - dp(1.1f), y - barHeight / 2f, x + dp(1.1f), y + barHeight / 2f)
            canvas.drawRoundRect(rect, dp(2f), dp(2f), paint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textSize = sp(7.7f)
        textPaint.color = Color.argb(210, 169, 239, 245)
        canvas.drawText("VOICE ARRAY // ${voiceLabel()} // LIVE RMS ${(displayVoiceEnergy * 100).toInt()}%", centerX, y + dp(42f), textPaint)
    }

    private fun drawCommandDock(canvas: Canvas, t: Float) {
        val left = width * 0.045f
        val right = width * 0.955f
        val top = height * 0.695f
        val bottom = height * 0.94f
        val accent = modeColor()
        rect.set(left, top, right, bottom)

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            left,
            top,
            right,
            bottom,
            intArrayOf(Color.argb(100, 2, 20, 31), Color.argb(78, 17, 7, 34), Color.argb(100, 2, 20, 31)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(145, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)

        val headerY = top + dp(22f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textSize = sp(8.5f)
        textPaint.color = accent
        canvas.drawText("COMMAND STREAM", left + dp(14f), headerY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = sp(7.2f)
        textPaint.color = Color.argb(190, 140, 220, 230)
        canvas.drawText("TRACE ${trace.lastOrNull().orEmpty().take(27).uppercase(Locale.US)}", right - dp(14f), headerY, textPaint)

        paint.strokeWidth = dp(0.6f)
        paint.color = Color.argb(75, 0, 230, 255)
        canvas.drawLine(left + dp(14f), top + dp(31f), right - dp(14f), top + dp(31f), paint)

        val contentWidth = right - left - dp(28f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(7.9f)
        textPaint.color = Color.argb(235, 220, 250, 252)
        val transcriptText = transcript.ifBlank { "Speak naturally. No hold-to-talk control is active." }
        val transcriptLines = wrapTextLines("YOU  //  $transcriptText", contentWidth, textPaint)
        transcriptLines.take(3).forEachIndexed { index, line ->
            canvas.drawText(line, left + dp(14f), top + dp(49f) + index * dp(13.5f), textPaint)
        }

        val responseTop = top + dp(94f)
        val responseBottom = bottom - dp(20f)
        val lineHeight = dp(13.5f)
        val linesPerPage = ((responseBottom - responseTop) / lineHeight).toInt().coerceAtLeast(3)

        textPaint.color = Color.argb(225, 175, 239, 246)
        textPaint.textSize = sp(7.7f)
        val responseLines = wrapTextLines("JARVIS  //  $lastResponse", contentWidth, textPaint)
        val pageCount = ((responseLines.size + linesPerPage - 1) / linesPerPage).coerceAtLeast(1)
        val elapsed = (SystemClock.uptimeMillis() - lastResponseUpdatedAtMs).coerceAtLeast(0L)
        val pageIndex = if (pageCount <= 1) 0 else ((elapsed / RESPONSE_PAGE_MS) % pageCount).toInt()
        val pageLines = responseLines.drop(pageIndex * linesPerPage).take(linesPerPage)
        pageLines.forEachIndexed { index, line ->
            canvas.drawText(line, left + dp(14f), responseTop + index * lineHeight, textPaint)
        }

        if (pageCount > 1) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = sp(6.8f)
            textPaint.color = Color.argb(185, 118, 205, 218)
            canvas.drawText("RESPONSE PAGE ${pageIndex + 1}/$pageCount", right - dp(14f), bottom - dp(7f), textPaint)
        }

        val pulseX = left + ((t * dp(52f)) % max(dp(1f), right - left))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 0, 255, 225)
        canvas.drawCircle(pulseX.coerceIn(left + dp(8f), right - dp(8f)), bottom - dp(8f), dp(1.5f), paint)
    }

    private fun drawEventRail(canvas: Canvas) {
        val y = height * 0.968f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(7.1f)
        textPaint.color = Color.argb(185, 118, 205, 218)
        val event = events.firstOrNull().orEmpty()
        canvas.drawText("EVENT // $event", width / 2f, y, textPaint)
    }

    private fun drawBootOverlay(canvas: Canvas, t: Float) {
        if (t > BOOT_SECONDS) return
        val fade = if (t < BOOT_SECONDS - 0.7f) 1f else ((BOOT_SECONDS - t) / 0.7f).coerceIn(0f, 1f)
        val alpha = (245 * fade).toInt()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 1, 4, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val progress = (t / BOOT_SECONDS).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.19f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(2.4f)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = Color.argb((80 * fade).toInt(), 0, 230, 255)
        canvas.drawArc(rect, -90f, 360f, false, paint)
        paint.color = Color.argb((235 * fade).toInt(), 0, 255, 225)
        canvas.drawArc(rect, -90f, 360f * progress, false, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textSize = sp(18f)
        textPaint.color = Color.argb(alpha, 0, 255, 230)
        textPaint.setShadowLayer(dp(12f), 0f, 0f, Color.argb((150 * fade).toInt(), 0, 255, 230))
        canvas.drawText("JARVIS", cx, cy - dp(18f), textPaint)
        textPaint.clearShadowLayer()

        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.textSize = sp(8.5f)
        textPaint.color = Color.argb((220 * fade).toInt(), 190, 245, 250)
        val stage = when {
            progress < 0.24f -> "GEOMETRY MATRIX"
            progress < 0.48f -> "VOICE ARRAY"
            progress < 0.72f -> "CORTEX MESH"
            progress < 0.94f -> "HEALTH ROUTER"
            else -> "SYSTEM ONLINE"
        }
        canvas.drawText("$stage // ${(progress * 100).toInt()}%", cx, cy + dp(15f), textPaint)

        val barWidth = min(width * 0.58f, dp(320f))
        val barHeight = dp(3f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((75 * fade).toInt(), 80, 170, 185)
        canvas.drawRoundRect(cx - barWidth / 2f, cy + dp(38f), cx + barWidth / 2f, cy + dp(38f) + barHeight, barHeight, barHeight, paint)
        paint.color = Color.argb((235 * fade).toInt(), 0, 255, 225)
        canvas.drawRoundRect(cx - barWidth / 2f, cy + dp(38f), cx - barWidth / 2f + barWidth * progress, cy + dp(38f) + barHeight, barHeight, barHeight, paint)
    }

    private fun wrapTextLines(text: String, maxWidth: Float, painter: Paint): List<String> {
        val output = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                output += ""
                return@forEach
            }
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (painter.measureText(candidate) <= maxWidth || line.isBlank()) {
                    line = candidate
                } else {
                    output += line
                    line = word
                }
            }
            if (line.isNotBlank()) output += line
        }
        return output.ifEmpty { listOf("") }
    }

    private fun voiceLabel(): String = when (voiceState) {
        VoiceLoop.State.READY -> "READY"
        VoiceLoop.State.LISTENING -> "LISTENING"
        VoiceLoop.State.PROCESSING -> "PROCESSING"
        VoiceLoop.State.ERROR -> "RECALIBRATING"
        VoiceLoop.State.UNAVAILABLE -> "UNAVAILABLE"
    }

    private fun modeColor(): Int = when (mode) {
        BrainMode.BOOT -> Color.rgb(0, 224, 255)
        BrainMode.ALERT -> Color.rgb(255, 68, 94)
        BrainMode.STEALTH -> Color.rgb(100, 124, 255)
        BrainMode.TACTICAL -> Color.rgb(255, 205, 82)
        BrainMode.LEARNING -> Color.rgb(160, 255, 120)
        BrainMode.EXECUTING -> Color.rgb(255, 164, 76)
        BrainMode.SECURITY -> Color.rgb(255, 86, 220)
        BrainMode.THINKING -> Color.rgb(111, 255, 183)
        BrainMode.LISTENING -> Color.rgb(0, 255, 224)
        BrainMode.ONLINE -> Color.rgb(0, 230, 255)
    }

    private fun drawHex(canvas: Canvas, cx: Float, cy: Float, radius: Float, painter: Paint) {
        path.reset()
        for (i in 0..6) {
            val angle = (PI / 3.0 * i + PI / 6.0).toFloat()
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, painter)
    }

    private fun pulse(t: Float, speed: Float): Float = ((sin((t * speed).toDouble()).toFloat() + 1f) * 0.5f)

    private fun Float.toRad(): Float = (this * PI / 180.0).toFloat()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private data class Particle(
        val x: Float,
        val y: Float,
        val speed: Float,
        val phase: Float,
        val size: Float
    )

    companion object {
        private const val BOOT_SECONDS = 3.8f
        private const val MAX_STREAM_TEXT = 12_000
        private const val RESPONSE_PAGE_MS = 4_500L
    }
}
