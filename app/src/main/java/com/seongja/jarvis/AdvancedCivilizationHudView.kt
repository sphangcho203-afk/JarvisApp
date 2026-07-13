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
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.jarvis.core.device.DeviceTelemetry
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

/**
 * JARVIS // HELIX
 *
 * A state-driven cinematic HUD. Every large movement communicates listening,
 * cognition, research, execution, speech, or recovery. Decorative motion is
 * intentionally slower and dimmer than operational motion.
 */
class AdvancedCivilizationHudView(context: Context) : View(context) {

    private enum class VisualState {
        BOOT,
        READY,
        LISTENING,
        THINKING,
        RESEARCHING,
        EXECUTING,
        SPEAKING,
        SUCCESS,
        WARNING,
        ERROR,
        STANDBY
    }

    private data class Particle(
        val x: Float,
        val y: Float,
        val depth: Float,
        val phase: Float,
        val size: Float
    )

    private val handler = Handler(Looper.getMainLooper())
    private val telemetry = DeviceTelemetry(context.applicationContext)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val bootStartedAt = SystemClock.uptimeMillis()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    private val particles = List(58) {
        Particle(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            depth = Random.nextFloat() * 0.75f + 0.25f,
            phase = Random.nextFloat() * 360f,
            size = Random.nextFloat() * 1.4f + 0.45f
        )
    }

    private val events = mutableListOf("HELIX VISUAL KERNEL ONLINE")

    private var transcript = ""
    private var lastResponse = "Neural command channel standing by."
    private var lastResponseUpdatedAtMs = SystemClock.uptimeMillis()
    private var intent = "standby"
    private var confidence = 0f
    private var memory = "OPERATOR MEMORY // SECURE"
    private var trace = listOf("helix_boot", "voice_array_ready")
    private var thoughts = listOf("Awaiting operator input.")
    private var entities = emptyList<String>()
    private var decision = "standby"
    private var mode = BrainMode.BOOT
    private var voiceState = VoiceLoop.State.READY
    private var commandCount = 0

    private var visualState = VisualState.BOOT
    private var previousVisualState = VisualState.BOOT
    private var stateChangedAtMs = SystemClock.uptimeMillis()
    private var targetVoiceEnergy = 0f
    private var displayVoiceEnergy = 0f
    private var responsePulse = 0f

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
            handler.postDelayed(this, FRAME_DELAY_MS)
        }
    }

    init {
        isFocusable = true
        isClickable = true
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        handler.post(animator)
    }

    fun setVoiceState(state: VoiceLoop.State) {
        voiceState = state
        when (state) {
            VoiceLoop.State.LISTENING -> transitionTo(VisualState.LISTENING)
            VoiceLoop.State.PROCESSING -> transitionTo(
                if (isResearchRequest()) VisualState.RESEARCHING else VisualState.THINKING
            )
            VoiceLoop.State.ERROR -> transitionTo(VisualState.ERROR)
            VoiceLoop.State.UNAVAILABLE -> transitionTo(VisualState.WARNING)
            VoiceLoop.State.READY -> {
                if (visualState in setOf(
                        VisualState.BOOT,
                        VisualState.LISTENING,
                        VisualState.THINKING,
                        VisualState.RESEARCHING,
                        VisualState.SPEAKING
                    )
                ) {
                    transitionTo(VisualState.READY)
                }
            }
        }
    }

    fun setVoiceAmplitude(value: Float) {
        targetVoiceEnergy = value.coerceIn(0f, 1f)
    }

    fun setProcessing(processing: Boolean) {
        if (processing) {
            transitionTo(if (isResearchRequest()) VisualState.RESEARCHING else VisualState.THINKING)
        } else if (visualState == VisualState.THINKING || visualState == VisualState.RESEARCHING) {
            transitionTo(VisualState.READY)
        }
    }

    fun setTranscript(value: String) {
        transcript = value.take(MAX_STREAM_TEXT)
        if (visualState == VisualState.THINKING && isResearchRequest()) {
            transitionTo(VisualState.RESEARCHING)
        }
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
        trace = response.trace.takeLast(10)
        thoughts = response.thoughts.takeLast(4)
        entities = response.entities.takeLast(8)
        decision = response.decision
        memory = response.memory
        responsePulse = 1f

        val next = when {
            response.mode == BrainMode.ALERT -> VisualState.ERROR
            response.mode == BrainMode.EXECUTING || response.mode == BrainMode.TACTICAL -> VisualState.EXECUTING
            response.intent.startsWith("web_research/") -> VisualState.SPEAKING
            response.intent.startsWith("device/") -> VisualState.SUCCESS
            else -> VisualState.SPEAKING
        }
        transitionTo(next)
        pushEvent("CORTEX -> ${response.intent.uppercase(Locale.US)} ${(response.confidence * 100).toInt()}%")
    }

    fun pushEvent(event: String) {
        val clean = event.take(88)
        events.add(0, clean)
        while (events.size > 12) events.removeLast()

        val upper = clean.uppercase(Locale.US)
        when {
            "VOICE -> SPEAKING" in upper -> transitionTo(VisualState.SPEAKING)
            "VOICE -> COMPLETE" in upper -> transitionTo(VisualState.READY)
            "ROUTING REQUEST" in upper -> transitionTo(
                if (isResearchRequest()) VisualState.RESEARCHING else VisualState.THINKING
            )
            "FAILED" in upper || " ERROR" in upper || upper.startsWith("ERROR") -> transitionTo(VisualState.ERROR)
            "PERMISSION" in upper || "CONFIRMATION" in upper -> transitionTo(VisualState.WARNING)
            "ACTION ->" in upper && "SUCCESS" in upper -> transitionTo(VisualState.SUCCESS)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val t = (now - bootStartedAt) / 1000f
        refreshTelemetry()
        updateAnimationState()
        resolveTransientState(now)

        drawVoid(canvas, t)
        drawDepthGrid(canvas, t)
        drawParticleField(canvas, t)
        drawFrame(canvas, t)
        drawHeader(canvas, t)
        drawCortexRail(canvas, t)
        drawContextModules(canvas, t)
        drawHelixCore(canvas, t)
        drawVoiceRibbon(canvas, t)
        drawCommandSurface(canvas, t)
        drawEventRail(canvas)
        drawBootSequence(canvas, t)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun transitionTo(next: VisualState) {
        if (next == visualState) return
        previousVisualState = visualState
        visualState = next
        stateChangedAtMs = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun resolveTransientState(now: Long) {
        val elapsed = now - stateChangedAtMs
        if (visualState == VisualState.SUCCESS && elapsed > 2_400L) transitionTo(VisualState.READY)
        if (visualState == VisualState.WARNING && elapsed > 5_500L && voiceState == VoiceLoop.State.READY) {
            transitionTo(VisualState.READY)
        }
        if (visualState == VisualState.SPEAKING && elapsed > 30_000L && voiceState == VoiceLoop.State.READY) {
            transitionTo(VisualState.READY)
        }
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
        val desired = if (visualState == VisualState.LISTENING) targetVoiceEnergy else 0f
        val responseFactor = if (desired > displayVoiceEnergy) 0.34f else 0.10f
        displayVoiceEnergy += (desired - displayVoiceEnergy) * responseFactor
        if (displayVoiceEnergy < 0.004f) displayVoiceEnergy = 0f
        responsePulse *= 0.94f
        if (responsePulse < 0.004f) responsePulse = 0f
    }

    private fun drawVoid(canvas: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(5, 8, 16),
                Color.rgb(7, 14, 25),
                Color.rgb(10, 9, 24),
                Color.rgb(4, 7, 13)
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val accent = stateColor()
        val centerX = width / 2f
        val centerY = height * 0.37f
        val glowRadius = min(width, height) * 0.54f
        paint.shader = RadialGradient(
            centerX,
            centerY,
            glowRadius,
            intArrayOf(
                Color.argb(22 + (stateIntensity() * 24).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(8, 16, 70, 92),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.44f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, paint)
        paint.shader = null

        if (visualState in setOf(VisualState.RESEARCHING, VisualState.ERROR, VisualState.WARNING)) {
            val scanY = ((t * dp(if (visualState == VisualState.RESEARCHING) 58f else 34f)) % (height + dp(120f))) - dp(60f)
            paint.shader = LinearGradient(
                0f,
                scanY - dp(30f),
                0f,
                scanY + dp(30f),
                intArrayOf(Color.TRANSPARENT, withAlpha(accent, 22), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, scanY - dp(30f), width.toFloat(), scanY + dp(30f), paint)
            paint.shader = null
        }
    }

    private fun drawDepthGrid(canvas: Canvas, t: Float) {
        val horizon = height * 0.61f
        val centerX = width / 2f
        val accent = stateColor()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.45f)
        paint.color = Color.argb(25, Color.red(accent), Color.green(accent), Color.blue(accent))

        for (i in 0..18) {
            val bottomX = width * (i / 18f)
            val topX = centerX + (bottomX - centerX) * 0.09f
            canvas.drawLine(topX, horizon, bottomX, height.toFloat(), paint)
        }

        val phase = (t * 0.12f) % 1f
        for (i in 0 until 15) {
            val p = ((i + phase) / 15f).coerceIn(0f, 1f)
            val eased = p * p
            val y = horizon + eased * (height - horizon)
            val inset = (1f - eased) * width * 0.45f
            canvas.drawLine(inset, y, width - inset, y, paint)
        }

        paint.color = Color.argb(16, 105, 118, 185)
        val radius = dp(18f)
        var y = height * 0.10f
        while (y < horizon) {
            var x = -radius
            val row = (y / (radius * 2.4f)).toInt()
            if (row % 2 != 0) x += radius * 1.55f
            while (x < width + radius) {
                drawHex(canvas, x, y, radius, paint)
                x += radius * 3.1f
            }
            y += radius * 2.7f
        }
    }

    private fun drawParticleField(canvas: Canvas, t: Float) {
        val accent = stateColor()
        val activity = stateIntensity()
        paint.style = Paint.Style.FILL
        particles.forEachIndexed { index, particle ->
            val drift = (particle.phase + t * (3f + particle.depth * 8f)).toRad()
            val x = ((particle.x * width) + sin(drift) * dp(10f) + width) % width
            val y = ((particle.y * height) + t * particle.depth * dp(2.2f) + height) % height
            val pulse = (sin((t * 1.4f + index * 0.63f).toDouble()).toFloat() + 1f) * 0.5f
            val alpha = (14 + particle.depth * 28 + pulse * 26 + activity * 20).toInt().coerceIn(0, 104)
            paint.color = if (index % 11 == 0) {
                Color.argb(alpha, 148, 106, 255)
            } else {
                Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            canvas.drawCircle(x, y, dp(particle.size * particle.depth), paint)
        }
    }

    private fun drawFrame(canvas: Canvas, t: Float) {
        val accent = stateColor()
        val inset = dp(14f)
        val arm = dp(54f)
        val alpha = 105 + (pulse(t, 1.8f) * 36).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))

        cornerPath(canvas, inset, inset, arm, true, true)
        cornerPath(canvas, width - inset, inset, arm, false, true)
        cornerPath(canvas, inset, height - inset, arm, true, false)
        cornerPath(canvas, width - inset, height - inset, arm, false, false)

        val progress = ((t * 0.075f) % 1f)
        val startX = dp(28f)
        val endX = width - dp(28f)
        val x = startX + (endX - startX) * progress
        paint.strokeWidth = dp(1.8f)
        paint.color = withAlpha(accent, 190)
        canvas.drawLine(x, dp(94f), min(endX, x + dp(28f)), dp(94f), paint)
    }

    private fun cornerPath(canvas: Canvas, x: Float, y: Float, arm: Float, left: Boolean, top: Boolean) {
        path.reset()
        path.moveTo(x, y + if (top) arm else -arm)
        path.lineTo(x, y)
        path.lineTo(x + if (left) arm else -arm, y)
        canvas.drawPath(path, paint)
    }

    private fun drawHeader(canvas: Canvas, t: Float) {
        val accent = stateColor()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(17.2f)
        textPaint.color = withAlpha(accent, 245)
        textPaint.setShadowLayer(dp(8f), 0f, 0f, withAlpha(accent, 90))
        canvas.drawText("JARVIS // HELIX", width / 2f, dp(37f), textPaint)
        textPaint.clearShadowLayer()

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = sp(8.3f)
        textPaint.color = Color.argb(205, 177, 218, 229)
        canvas.drawText("ADAPTIVE INTELLIGENCE LAYER  •  CINEMATIC KERNEL", width / 2f, dp(55f), textPaint)

        drawStatusPill(canvas, width / 2f, dp(73f), stateTitle(), accent)

        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = sp(7.9f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.argb(185, 128, 193, 209)
        canvas.drawText("$cachedTime  //  $cachedNetwork", dp(24f), dp(91f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BAT $cachedBattery%  //  HEAP ${cachedHeapMb}MB", width - dp(24f), dp(91f), textPaint)
    }

    private fun drawStatusPill(canvas: Canvas, cx: Float, cy: Float, label: String, color: Int) {
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(7.2f)
        val padding = dp(13f)
        val textWidth = textPaint.measureText(label)
        rect.set(cx - textWidth / 2f - padding, cy - dp(9f), cx + textWidth / 2f + padding, cy + dp(6f))
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(color, 18)
        canvas.drawRoundRect(rect, dp(9f), dp(9f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.8f)
        paint.color = withAlpha(color, 115)
        canvas.drawRoundRect(rect, dp(9f), dp(9f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = withAlpha(color, 235)
        canvas.drawText(label, cx, cy + dp(1f), textPaint)
    }

    private fun drawCortexRail(canvas: Canvas, t: Float) {
        val y = height * 0.105f
        val left = width * 0.18f
        val right = width * 0.82f
        val accent = stateColor()
        val active = activeNodeIndex()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.7f)
        paint.color = Color.argb(54, 115, 180, 196)
        canvas.drawLine(left, y, right, y, paint)

        for (index in 0 until 10) {
            val p = index / 9f
            val x = left + (right - left) * p
            val nodeActive = index == active
            val radius = dp(if (nodeActive) 3.3f else 1.7f)
            paint.style = Paint.Style.FILL
            paint.color = if (nodeActive) accent else Color.argb(115, 112, 178, 194)
            canvas.drawCircle(x, y, radius + if (nodeActive) pulse(t, 3.2f) * dp(0.8f) else 0f, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.MONOSPACE
            textPaint.textSize = sp(5.7f)
            textPaint.color = if (nodeActive) withAlpha(accent, 235) else Color.argb(115, 128, 188, 199)
            val label = if (index < 6) "G${index + 1}" else "Q${index - 5}"
            canvas.drawText(label, x, y + dp(12f), textPaint)
        }
    }

    private fun drawContextModules(canvas: Canvas, t: Float) {
        val top = height * 0.145f
        val w = width * 0.275f
        val h = height * 0.122f
        val margin = width * 0.045f
        val activeAlpha = if (visualState == VisualState.READY) 0.55f else 1f

        val leftLines = listOf(
            "INTENT  ${intentLabel()}",
            "CONF    ${(confidence * 100).toInt()}%",
            "ROUTE   ${routeLabel()}",
            "DEC     ${decision.take(18).uppercase(Locale.US)}"
        )
        drawModule(canvas, margin, top, w, h, "COGNITION", leftLines, activeAlpha, t)

        val rightLines = listOf(
            "VOICE   ${voiceLabel()}",
            "RMS     ${(displayVoiceEnergy * 100).toInt()}%",
            "CMDS    $commandCount",
            "NODE    ${activeNodeLabel()}"
        )
        drawModule(canvas, width - margin - w, top, w, h, "SYSTEM", rightLines, activeAlpha, t + 0.7f)
    }

    private fun drawModule(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        lines: List<String>,
        visibility: Float,
        t: Float
    ) {
        val accent = stateColor()
        rect.set(x, y, x + w, y + h)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            x,
            y,
            x + w,
            y + h,
            intArrayOf(
                Color.argb((62 * visibility).toInt(), 9, 28, 39),
                Color.argb((28 * visibility).toInt(), 24, 12, 43)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.85f)
        paint.color = withAlpha(accent, ((82 + pulse(t, 1.6f) * 34) * visibility).toInt())
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)

        paint.strokeWidth = dp(1.8f)
        canvas.drawLine(x + dp(10f), y, x + w * 0.42f, y, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(7.4f)
        textPaint.color = withAlpha(accent, (235 * visibility).toInt())
        canvas.drawText(title, x + dp(10f), y + dp(17f), textPaint)

        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = sp(6.7f)
        textPaint.color = Color.argb((204 * visibility).toInt(), 196, 228, 235)
        val gap = (h - dp(29f)) / 4f
        lines.take(4).forEachIndexed { index, line ->
            canvas.drawText(line.take(25), x + dp(10f), y + dp(35f) + gap * index, textPaint)
        }
    }

    private fun drawHelixCore(canvas: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height * 0.39f
        val base = min(width * 0.205f, height * 0.112f)
        val accent = stateColor()
        val intensity = stateIntensity()
        val transition = ((SystemClock.uptimeMillis() - stateChangedAtMs) / 650f).coerceIn(0f, 1f)
        val breathing = 1f + pulse(t, if (visualState == VisualState.READY) 0.65f else 1.45f) * 0.025f
        val activeScale = 1f + intensity * 0.08f + displayVoiceEnergy * 0.10f
        val coreBase = base * breathing * activeScale

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx,
            cy,
            coreBase * 1.9f,
            intArrayOf(
                withAlpha(accent, 60 + (intensity * 50).toInt()),
                withAlpha(accent, 18),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreBase * 1.9f, paint)
        paint.shader = null

        val ringCount = if (visualState == VisualState.RESEARCHING) 8 else 6
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (index in 0 until ringCount) {
            val radius = coreBase * (0.54f + index * 0.16f)
            val direction = if (index % 2 == 0) 1f else -1f
            val speed = stateRotationSpeed() * (1f + index * 0.15f)
            val start = (t * speed * direction + index * 43f) % 360f
            val sweep = 25f + (index % 3) * 19f + intensity * 18f
            paint.strokeWidth = dp(if (index % 3 == 0) 2.15f else 1.0f)
            paint.color = if (index % 4 == 3) {
                Color.argb(170, 142, 96, 255)
            } else {
                withAlpha(accent, 135 + index * 8)
            }
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, start, sweep, false, paint)
            canvas.drawArc(rect, start + 180f, sweep * 0.58f, false, paint)
        }

        drawStateScanner(canvas, cx, cy, coreBase * 1.46f, t, accent)
        drawCoreNodes(canvas, cx, cy, coreBase, t, accent)

        val nucleusRadius = coreBase * (0.20f + displayVoiceEnergy * 0.05f + responsePulse * 0.04f)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(accent, 22)
        canvas.drawCircle(cx, cy, nucleusRadius * 3.2f, paint)
        paint.color = withAlpha(accent, 64)
        canvas.drawCircle(cx, cy, nucleusRadius * 2.1f, paint)
        paint.color = withAlpha(accent, 155)
        canvas.drawCircle(cx, cy, nucleusRadius * 1.45f, paint)
        paint.color = accent
        canvas.drawCircle(cx, cy, nucleusRadius, paint)
        paint.color = Color.argb(240, 239, 255, 255)
        canvas.drawCircle(cx, cy, nucleusRadius * 0.31f, paint)

        drawCountdownRing(canvas, cx, cy, coreBase, accent)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(if (countdownActive) 14.8f else 9.2f)
        textPaint.color = Color.argb((180 + 60 * transition).toInt(), 235, 255, 255)
        canvas.drawText(if (countdownActive) timerText() else stateTitle(), cx, cy + dp(4f), textPaint)

        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = sp(6.3f)
        textPaint.color = Color.argb(190, 151, 214, 225)
        canvas.drawText(
            if (countdownActive) countdownLabel.take(24) else stateSubtitle(t),
            cx,
            cy + dp(21f),
            textPaint
        )
    }

    private fun drawStateScanner(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float, accent: Int) {
        val shouldScan = visualState in setOf(
            VisualState.THINKING,
            VisualState.RESEARCHING,
            VisualState.EXECUTING,
            VisualState.ERROR
        )
        if (!shouldScan) return
        canvas.save()
        canvas.rotate((t * stateRotationSpeed() * 0.8f) % 360f, cx, cy)
        paint.style = Paint.Style.FILL
        paint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.TRANSPARENT,
                withAlpha(accent, 4),
                withAlpha(accent, 68),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.78f, 0.96f, 1f)
        )
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 0f, 360f, true, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun drawCoreNodes(canvas: Canvas, cx: Float, cy: Float, base: Float, t: Float, accent: Int) {
        val count = when (visualState) {
            VisualState.RESEARCHING -> 14
            VisualState.THINKING -> 10
            VisualState.EXECUTING -> 8
            else -> 6
        }
        paint.strokeWidth = dp(0.55f)
        for (index in 0 until count) {
            val angle = (index * (360f / count) + t * if (index % 2 == 0) 7f else -5f).toRad()
            val radius = base * (1.12f + (index % 3) * 0.12f)
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(if (visualState == VisualState.RESEARCHING) 82 else 45, 174, 226, 235)
            canvas.drawLine(cx, cy, x, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (index % 5 == 0) Color.rgb(153, 92, 255) else accent
            canvas.drawCircle(x, y, dp(if (index % 4 == 0) 2.4f else 1.25f), paint)
        }
    }

    private fun drawCountdownRing(canvas: Canvas, cx: Float, cy: Float, base: Float, accent: Int) {
        if (countdownTotalMs <= 0L) return
        val radius = base * 1.66f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(3f)
        paint.color = Color.argb(35, 210, 240, 255)
        canvas.drawArc(rect, -90f, 360f, false, paint)
        paint.shader = SweepGradient(cx, cy, intArrayOf(accent, Color.rgb(155, 90, 255), accent), null)
        paint.color = Color.WHITE
        canvas.drawArc(rect, -90f, 360f * countdownProgress, false, paint)
        paint.shader = null
    }

    private fun drawVoiceRibbon(canvas: Canvas, t: Float) {
        val centerX = width / 2f
        val y = height * 0.625f
        val totalWidth = width * 0.76f
        val bars = 55
        val step = totalWidth / bars
        val accent = stateColor()
        val activeBoost = when (visualState) {
            VisualState.LISTENING -> 1f
            VisualState.SPEAKING -> 0.72f
            VisualState.RESEARCHING -> 0.42f
            else -> 0.22f
        }

        paint.style = Paint.Style.FILL
        for (index in 0 until bars) {
            val normalized = (index - bars / 2f) / (bars / 2f)
            val envelope = (1f - abs(normalized)).coerceAtLeast(0.14f)
            val wave = abs(sin((t * (2.3f + activeBoost * 2f) + index * 0.47f).toDouble())).toFloat()
            val live = displayVoiceEnergy * envelope
            val syntheticSpeech = if (visualState == VisualState.SPEAKING) wave * envelope * 0.42f else 0f
            val researchPulse = if (visualState == VisualState.RESEARCHING) wave * 0.18f else 0f
            val value = 0.06f + live * 0.85f + syntheticSpeech + researchPulse
            val h = dp(4f) + value * dp(39f)
            val x = centerX + (index - bars / 2f) * step
            val alpha = (74 + value * 175).toInt().coerceIn(0, 255)
            paint.color = if (index % 9 == 0) {
                Color.argb(alpha, 147, 92, 255)
            } else {
                Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            rect.set(x - dp(1.0f), y - h / 2f, x + dp(1.0f), y + h / 2f)
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), paint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(6.6f)
        textPaint.color = Color.argb(185, 157, 213, 223)
        canvas.drawText("VOICE ARRAY // ${voiceLabel()} // RMS ${(displayVoiceEnergy * 100).toInt()}%", centerX, y + dp(35f), textPaint)
    }

    private fun drawCommandSurface(canvas: Canvas, t: Float) {
        val left = width * 0.045f
        val right = width * 0.955f
        val top = height * 0.675f
        val bottom = height * 0.945f
        val accent = stateColor()
        rect.set(left, top, right, bottom)

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            left,
            top,
            right,
            bottom,
            intArrayOf(
                Color.argb(112, 8, 24, 35),
                Color.argb(78, 16, 12, 34),
                Color.argb(108, 7, 22, 31)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.9f)
        paint.color = withAlpha(accent, 125)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        paint.strokeWidth = dp(2f)
        canvas.drawLine(left + dp(16f), top, left + (right - left) * 0.44f, top, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(8.0f)
        textPaint.color = withAlpha(accent, 240)
        canvas.drawText("COMMAND CHANNEL", left + dp(15f), top + dp(23f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = sp(6.5f)
        textPaint.color = Color.argb(170, 132, 196, 207)
        canvas.drawText("${stateTitle()}  //  ${activeNodeLabel()}", right - dp(15f), top + dp(23f), textPaint)

        paint.strokeWidth = dp(0.55f)
        paint.color = Color.argb(55, 91, 184, 203)
        canvas.drawLine(left + dp(15f), top + dp(33f), right - dp(15f), top + dp(33f), paint)

        val contentWidth = right - left - dp(30f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = sp(8.1f)
        textPaint.color = Color.argb(235, 222, 242, 246)
        val heard = transcript.ifBlank { "Listening channel ready." }
        val heardLines = wrapTextLines("YOU // $heard", contentWidth, textPaint)
        heardLines.take(3).forEachIndexed { index, line ->
            canvas.drawText(line, left + dp(15f), top + dp(53f) + index * dp(14f), textPaint)
        }

        val responseTop = top + dp(103f)
        val responseBottom = bottom - dp(22f)
        val lineHeight = dp(14f)
        val linesPerPage = ((responseBottom - responseTop) / lineHeight).toInt().coerceAtLeast(3)
        textPaint.textSize = sp(7.8f)
        textPaint.color = Color.argb(225, 170, 222, 231)
        val responseLines = wrapTextLines("JARVIS // $lastResponse", contentWidth, textPaint)
        val pageCount = ((responseLines.size + linesPerPage - 1) / linesPerPage).coerceAtLeast(1)
        val elapsed = (SystemClock.uptimeMillis() - lastResponseUpdatedAtMs).coerceAtLeast(0L)
        val pageIndex = if (pageCount == 1) 0 else ((elapsed / RESPONSE_PAGE_MS) % pageCount).toInt()
        responseLines.drop(pageIndex * linesPerPage).take(linesPerPage).forEachIndexed { index, line ->
            canvas.drawText(line, left + dp(15f), responseTop + index * lineHeight, textPaint)
        }

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = sp(6.2f)
        textPaint.color = Color.argb(160, 111, 179, 192)
        val footer = if (pageCount > 1) {
            "PAGE ${pageIndex + 1}/$pageCount  //  ${sourceCount()} SOURCES"
        } else {
            "${sourceCount()} SOURCES  //  ${trace.lastOrNull().orEmpty().take(24).uppercase(Locale.US)}"
        }
        canvas.drawText(footer, right - dp(15f), bottom - dp(9f), textPaint)

        val pulseX = left + dp(16f) + ((t * dp(46f)) % max(dp(1f), right - left - dp(32f)))
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(accent, 150)
        canvas.drawCircle(pulseX, bottom - dp(9f), dp(1.35f), paint)
    }

    private fun drawEventRail(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = sp(6.5f)
        textPaint.color = Color.argb(150, 112, 177, 190)
        canvas.drawText("EVENT // ${events.firstOrNull().orEmpty()}", width / 2f, height * 0.972f, textPaint)
    }

    private fun drawBootSequence(canvas: Canvas, t: Float) {
        if (t > BOOT_SECONDS) return
        val progress = (t / BOOT_SECONDS).coerceIn(0f, 1f)
        val fade = if (progress < 0.80f) 1f else ((1f - progress) / 0.20f).coerceIn(0f, 1f)
        val alpha = (248 * fade).toInt()
        val accent = Color.rgb(82, 245, 207)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 4, 7, 14)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.19f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(2f)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = Color.argb((58 * fade).toInt(), 82, 245, 207)
        canvas.drawArc(rect, -90f, 360f, false, paint)
        paint.color = Color.argb((235 * fade).toInt(), 82, 245, 207)
        canvas.drawArc(rect, -90f, 360f * progress, false, paint)

        for (index in 0 until 4) {
            val ring = radius * (0.52f + index * 0.16f)
            rect.set(cx - ring, cy - ring, cx + ring, cy + ring)
            paint.strokeWidth = dp(if (index == 0) 1.7f else 0.8f)
            paint.color = Color.argb((145 * fade).toInt(), if (index == 3) 145 else 82, if (index == 3) 96 else 245, if (index == 3) 255 else 207)
            canvas.drawArc(rect, progress * 210f * if (index % 2 == 0) 1f else -1f, 52f + index * 16f, false, paint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(20f)
        textPaint.color = Color.argb(alpha, 224, 255, 248)
        textPaint.setShadowLayer(dp(14f), 0f, 0f, Color.argb((120 * fade).toInt(), 82, 245, 207))
        canvas.drawText("JARVIS", cx, cy - dp(17f), textPaint)
        textPaint.clearShadowLayer()

        textPaint.textSize = sp(8.0f)
        textPaint.color = Color.argb((215 * fade).toInt(), 151, 221, 224)
        val stage = when {
            progress < 0.22f -> "GEOMETRY MATRIX"
            progress < 0.44f -> "VOICE ARRAY"
            progress < 0.66f -> "CORTEX MESH"
            progress < 0.86f -> "ACTION FABRIC"
            else -> "SYSTEM READY"
        }
        canvas.drawText("$stage // ${(progress * 100).toInt()}%", cx, cy + dp(17f), textPaint)

        val barWidth = min(width * 0.58f, dp(330f))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((52 * fade).toInt(), 130, 183, 192)
        canvas.drawRoundRect(cx - barWidth / 2f, cy + dp(40f), cx + barWidth / 2f, cy + dp(43f), dp(2f), dp(2f), paint)
        paint.color = Color.argb((235 * fade).toInt(), 82, 245, 207)
        canvas.drawRoundRect(cx - barWidth / 2f, cy + dp(40f), cx - barWidth / 2f + barWidth * progress, cy + dp(43f), dp(2f), dp(2f), paint)
    }

    private fun isResearchRequest(): Boolean = runCatching {
        WebResearchIntent.shouldUseWeb(transcript)
    }.getOrDefault(false)

    private fun stateTitle(): String = when (visualState) {
        VisualState.BOOT -> "BOOTING"
        VisualState.READY -> "READY"
        VisualState.LISTENING -> "LISTENING"
        VisualState.THINKING -> "THINKING"
        VisualState.RESEARCHING -> "RESEARCHING"
        VisualState.EXECUTING -> "EXECUTING"
        VisualState.SPEAKING -> "SPEAKING"
        VisualState.SUCCESS -> "VERIFIED"
        VisualState.WARNING -> "CONFIRMATION"
        VisualState.ERROR -> "ALERT"
        VisualState.STANDBY -> "STANDBY"
    }

    private fun stateSubtitle(t: Float): String = when (visualState) {
        VisualState.BOOT -> "INITIALIZING"
        VisualState.READY -> "AWAITING COMMAND"
        VisualState.LISTENING -> "VOICE ARRAY ACTIVE"
        VisualState.THINKING -> when (((SystemClock.uptimeMillis() - stateChangedAtMs) / 1_700L).toInt() % 3) {
            0 -> "CLASSIFYING REQUEST"
            1 -> "SELECTING CORTEX"
            else -> "SYNTHESIZING"
        }
        VisualState.RESEARCHING -> when (((SystemClock.uptimeMillis() - stateChangedAtMs) / 2_200L).toInt() % 4) {
            0 -> "PLANNING QUERIES"
            1 -> "SCANNING SOURCES"
            2 -> "CROSS-CHECKING"
            else -> "ASSEMBLING BRIEF"
        }
        VisualState.EXECUTING -> "ACTION FABRIC ACTIVE"
        VisualState.SPEAKING -> "VOICE OUTPUT ACTIVE"
        VisualState.SUCCESS -> "ACTION CONFIRMED"
        VisualState.WARNING -> "OPERATOR INPUT REQUIRED"
        VisualState.ERROR -> "RECOVERY CHANNEL ACTIVE"
        VisualState.STANDBY -> "LOW ACTIVITY MODE"
    }

    private fun stateColor(): Int = when (visualState) {
        VisualState.BOOT -> Color.rgb(82, 210, 255)
        VisualState.READY -> Color.rgb(82, 245, 207)
        VisualState.LISTENING -> Color.rgb(45, 218, 255)
        VisualState.THINKING -> Color.rgb(142, 108, 255)
        VisualState.RESEARCHING -> Color.rgb(255, 184, 76)
        VisualState.EXECUTING -> Color.rgb(70, 238, 155)
        VisualState.SPEAKING -> Color.rgb(150, 225, 255)
        VisualState.SUCCESS -> Color.rgb(96, 255, 174)
        VisualState.WARNING -> Color.rgb(255, 180, 72)
        VisualState.ERROR -> Color.rgb(255, 72, 108)
        VisualState.STANDBY -> Color.rgb(92, 119, 160)
    }

    private fun stateIntensity(): Float = when (visualState) {
        VisualState.BOOT -> 0.65f
        VisualState.READY -> 0.18f
        VisualState.LISTENING -> 0.68f + displayVoiceEnergy * 0.32f
        VisualState.THINKING -> 0.62f
        VisualState.RESEARCHING -> 0.82f
        VisualState.EXECUTING -> 0.74f
        VisualState.SPEAKING -> 0.55f
        VisualState.SUCCESS -> 0.80f
        VisualState.WARNING -> 0.62f
        VisualState.ERROR -> 0.85f
        VisualState.STANDBY -> 0.08f
    }

    private fun stateRotationSpeed(): Float = when (visualState) {
        VisualState.READY, VisualState.STANDBY -> 5.5f
        VisualState.LISTENING -> 11f
        VisualState.THINKING -> 24f
        VisualState.RESEARCHING -> 31f
        VisualState.EXECUTING -> 21f
        VisualState.SPEAKING -> 9f
        VisualState.SUCCESS -> 15f
        VisualState.WARNING -> 13f
        VisualState.ERROR -> 18f
        VisualState.BOOT -> 17f
    }

    private fun voiceLabel(): String = when (voiceState) {
        VoiceLoop.State.READY -> "READY"
        VoiceLoop.State.LISTENING -> "LISTENING"
        VoiceLoop.State.PROCESSING -> "PROCESSING"
        VoiceLoop.State.ERROR -> "RECALIBRATING"
        VoiceLoop.State.UNAVAILABLE -> "UNAVAILABLE"
    }

    private fun intentLabel(): String = intent
        .replace('_', '/')
        .take(18)
        .uppercase(Locale.US)

    private fun routeLabel(): String = when {
        intent.startsWith("web_research/") -> "LIVE WEB"
        intent.startsWith("device/") -> "ANDROID"
        intent.startsWith("memory_") -> "MEMORY"
        else -> "CORTEX"
    }

    private fun activeNodeIndex(): Int {
        val joined = (trace + entities).joinToString(" ").uppercase(Locale.US)
        val gemini = Regex("GEMINI\\s*0?([1-6])").find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (gemini != null) return gemini - 1
        val groq = Regex("GROQ\\s*0?([1-4])").find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (groq != null) return 5 + groq
        return if (visualState == VisualState.RESEARCHING) 6 else -1
    }

    private fun activeNodeLabel(): String {
        val index = activeNodeIndex()
        return when {
            index in 0..5 -> "GEMINI ${index + 1}"
            index in 6..9 -> "GROQ ${index - 5}"
            else -> "AUTO"
        }
    }

    private fun sourceCount(): Int {
        val joined = entities.joinToString(" ")
        return Regex("sources=(\\d+)", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private fun timerText(): String {
        val totalSeconds = ((countdownRemainingMs + 999L) / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.US, minutes, seconds)
        }
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

    private fun drawHex(canvas: Canvas, cx: Float, cy: Float, radius: Float, painter: Paint) {
        path.reset()
        for (index in 0..6) {
            val angle = (PI / 3.0 * index + PI / 6.0).toFloat()
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, painter)
    }

    private fun pulse(t: Float, speed: Float): Float =
        (sin((t * speed).toDouble()).toFloat() + 1f) * 0.5f

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun Float.toRad(): Float = (this * PI / 180.0).toFloat()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        private const val FRAME_DELAY_MS = 16L
        private const val BOOT_SECONDS = 3.2f
        private const val MAX_STREAM_TEXT = 14_000
        private const val RESPONSE_PAGE_MS = 5_500L
    }
}
