package com.seongja.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class JarvisHudView(context: Context, private val engine: JarvisEngine) : View(context) {
    private val cyan = Color.rgb(0, 229, 255)
    private val blue = Color.rgb(22, 139, 255)
    private val amber = Color.rgb(255, 196, 87)
    private val deep = Color.rgb(1, 5, 14)
    private val panel = Color.rgb(5, 18, 36)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var state = engine.state
    var onTap: (() -> Unit)? = null

    private val listener: (JarvisUiState) -> Unit = {
        state = it
        invalidate()
    }

    init {
        setBackgroundColor(deep)
        isClickable = true
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        engine.addListener(listener)
    }

    override fun onDetachedFromWindow() {
        engine.removeListener(listener)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            onTap?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.39f
        val radius = min(w, h) * 0.285f
        val time = SystemClock.uptimeMillis() / 1000f

        drawAtmosphere(canvas, w, h, time)
        drawBackgroundGrid(canvas, w, h, time)
        drawCornerFrame(canvas, w, h, time)
        drawTelemetryRail(canvas, w, h)
        drawRadarCore(canvas, cx, cy, radius, time)
        drawWaveform(canvas, cx, cy + radius + 58f, w * 0.72f, time)
        drawTextPanel(canvas, w, h)

        postInvalidateDelayed(16L)
    }

    private fun drawAtmosphere(canvas: Canvas, w: Float, h: Float, time: Float) {
        paint.style = Paint.Style.FILL
        paint.color = deep
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.argb(26, 0, 229, 255)
        val glow = (sin(time * 1.8f) * 18f + 34f)
        canvas.drawCircle(w * 0.5f, h * 0.36f, min(w, h) * 0.44f + glow, paint)

        paint.color = Color.argb(18, 22, 139, 255)
        canvas.drawCircle(w * 0.5f, h * 0.36f, min(w, h) * 0.66f, paint)
    }

    private fun drawBackgroundGrid(canvas: Canvas, w: Float, h: Float, time: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(34, 0, 229, 255)
        val step = 42f
        val offset = (time * 18f) % step
        var x = -step + offset
        while (x < w + step) {
            canvas.drawLine(x, 0f, x, h, paint)
            x += step
        }
        var y = -step + offset
        while (y < h + step) {
            canvas.drawLine(0f, y, w, y, paint)
            y += step
        }

        paint.color = Color.argb(22, 255, 255, 255)
        var d = -h
        while (d < w) {
            canvas.drawLine(d, h, d + h, 0f, paint)
            d += step * 2f
        }
    }

    private fun drawCornerFrame(canvas: Canvas, w: Float, h: Float, time: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.argb(170, 0, 229, 255)
        val pad = 26f
        val len = 92f + sin(time * 2f) * 8f
        canvas.drawLine(pad, pad, pad + len, pad, paint)
        canvas.drawLine(pad, pad, pad, pad + len, paint)
        canvas.drawLine(w - pad, pad, w - pad - len, pad, paint)
        canvas.drawLine(w - pad, pad, w - pad, pad + len, paint)
        canvas.drawLine(pad, h - pad, pad + len, h - pad, paint)
        canvas.drawLine(pad, h - pad, pad, h - pad - len, paint)
        canvas.drawLine(w - pad, h - pad, w - pad - len, h - pad, paint)
        canvas.drawLine(w - pad, h - pad, w - pad, h - pad - len, paint)
    }

    private fun drawTelemetryRail(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(95, 0, 229, 255)
        canvas.drawRoundRect(22f, 148f, w - 22f, 206f, 18f, 18f, paint)

        drawChip(canvas, 38f, 164f, "MODE", state.mode, cyan)
        drawChip(canvas, w * 0.38f, 164f, "SIGNAL", state.signal, blue)
        drawChip(canvas, w * 0.68f, 164f, "CMDS", state.commandCount.toString(), amber)
    }

    private fun drawChip(canvas: Canvas, x: Float, y: Float, label: String, value: String, accent: Int) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textPaint.textSize = 12f
        textPaint.color = Color.argb(170, 255, 255, 255)
        canvas.drawText(label, x, y, textPaint)
        textPaint.textSize = 17f
        textPaint.color = accent
        canvas.drawText(value.take(12), x, y + 22f, textPaint)
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    private fun drawRadarCore(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float) {
        val pulse = 0.9f + state.energy * 0.16f
        paint.style = Paint.Style.STROKE

        for (i in 0..5) {
            paint.strokeWidth = 2.8f - i * 0.25f
            paint.color = Color.argb(140 - i * 17, 0, 229, 255)
            canvas.drawCircle(cx, cy, radius * pulse * (0.30f + i * 0.14f), paint)
        }

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.strokeWidth = 8f
        paint.color = Color.argb(165, 22, 139, 255)
        canvas.drawArc(rect, (time * 76f) % 360f, 54f, false, paint)

        paint.strokeWidth = 3f
        paint.color = Color.argb(220, 0, 229, 255)
        for (i in 0 until 24) {
            val angle = Math.toRadians((i * 15f + time * 14f).toDouble())
            val inner = if (i % 2 == 0) radius * 0.65f else radius * 0.73f
            val outer = radius * 0.91f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint
            )
        }

        drawOrbitingNodes(canvas, cx, cy, radius, time)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((190 + state.energy * 55f).toInt(), 0, 229, 255)
        canvas.drawCircle(cx, cy, radius * (0.12f + state.energy * 0.04f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.color = Color.argb(210, 255, 255, 255)
        drawDiamond(canvas, cx, cy, radius * 0.28f)
    }

    private fun drawOrbitingNodes(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float) {
        paint.style = Paint.Style.FILL
        for (i in 0 until 6) {
            val angle = Math.toRadians((time * 42f + i * 60f).toDouble())
            val orbit = radius * (0.94f + (i % 2) * 0.08f)
            val x = cx + cos(angle).toFloat() * orbit
            val y = cy + sin(angle).toFloat() * orbit
            paint.color = if (i % 2 == 0) Color.argb(220, 0, 229, 255) else Color.argb(210, 255, 196, 87)
            canvas.drawCircle(x, y, 5f + state.energy * 4f, paint)
        }
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r, cy)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r, cy)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawWaveform(canvas: Canvas, cx: Float, y: Float, width: Float, time: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(190, 0, 229, 255)
        path.reset()
        val start = cx - width / 2f
        val points = 96
        for (i in 0..points) {
            val x = start + width * i / points
            val amp = 12f + state.energy * 48f
            val wave = sin(i * 0.40f + time * 6f) * amp + sin(i * 0.11f - time * 3f) * amp * 0.38f
            if (i == 0) path.moveTo(x, y + wave) else path.lineTo(x, y + wave)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTextPanel(canvas: Canvas, w: Float, h: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textPaint.color = Color.WHITE
        textPaint.textSize = 38f
        canvas.drawText("JARVIS", w / 2f, 90f, textPaint)

        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        textPaint.textSize = 15f
        textPaint.color = if (state.listening) cyan else Color.argb(185, 255, 255, 255)
        canvas.drawText("STATUS: ${state.status}  |  UPTIME: ${state.uptimeSeconds}s", w / 2f, 121f, textPaint)

        val panelTop = h * 0.69f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(96, Color.red(panel), Color.green(panel), Color.blue(panel))
        canvas.drawRoundRect(28f, panelTop, w - 28f, h - 86f, 24f, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(145, 0, 229, 255)
        canvas.drawRoundRect(28f, panelTop, w - 28f, h - 86f, 24f, 24f, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.argb(230, 0, 229, 255)
        textPaint.textSize = 15f
        canvas.drawText("TRANSCRIPT", 52f, panelTop + 36f, textPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 19f
        drawMultiline(canvas, state.transcript.ifBlank { "Awaiting voice input." }, 52f, panelTop + 68f, w - 104f, 26f, 2)

        textPaint.color = Color.argb(230, 22, 139, 255)
        textPaint.textSize = 15f
        canvas.drawText("RESPONSE", 52f, panelTop + 132f, textPaint)
        textPaint.color = Color.argb(235, 255, 255, 255)
        textPaint.textSize = 18f
        drawMultiline(canvas, state.response, 52f, panelTop + 164f, w - 104f, 25f, 3)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(174, 255, 255, 255)
        textPaint.textSize = 14f
        canvas.drawText("Tap anywhere to toggle voice loop", w / 2f, h - 42f, textPaint)
    }

    private fun drawMultiline(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, lineHeight: Float, maxLines: Int) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (textPaint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && current.isNotBlank()) lines += current
        lines.take(maxLines).forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * lineHeight, textPaint)
        }
    }
}
