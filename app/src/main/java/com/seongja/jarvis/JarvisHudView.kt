package com.seongja.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class JarvisHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state = JarvisUiState()
    private var rotation = 0f
    private var pulse = 0f

    private val cyan = Color.rgb(0, 229, 255)
    private val blue = Color.rgb(41, 121, 255)
    private val white = Color.rgb(234, 251, 255)
    private val dim = Color.argb(130, 0, 229, 255)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = white
        textAlign = Paint.Align.CENTER
    }

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 8000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = it.animatedValue as Float
            pulse = ((rotation % 360f) / 360f)
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.rgb(5, 7, 12))
        animator.start()
    }

    fun render(newState: JarvisUiState) {
        state = newState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f - 40f
        val radius = min(w, h) * 0.27f

        drawGrid(canvas, w, h)
        drawCore(canvas, cx, cy, radius)
        drawTexts(canvas, cx, cy, radius)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        strokePaint.strokeWidth = 1f
        strokePaint.color = Color.argb(35, 0, 229, 255)
        val step = 48f
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, strokePaint)
            x += step
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, strokePaint)
            y += step
        }
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        strokePaint.strokeWidth = 4f
        strokePaint.color = if (state.isListening) cyan else dim
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, rotation, 250f, false, strokePaint)
        canvas.drawArc(rect, -rotation * 0.7f, 160f, false, strokePaint)

        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(180, 41, 121, 255)
        canvas.drawCircle(cx, cy, radius * 0.72f, strokePaint)
        canvas.drawCircle(cx, cy, radius * 1.22f, strokePaint)

        fillPaint.color = Color.argb(if (state.isListening) 105 else 55, 0, 229, 255)
        canvas.drawCircle(cx, cy, radius * (0.22f + pulse * 0.08f), fillPaint)

        val nodes = 18
        for (i in 0 until nodes) {
            val angle = Math.toRadians((i * (360.0 / nodes)) + rotation)
            val x = cx + cos(angle).toFloat() * radius * 1.08f
            val y = cy + sin(angle).toFloat() * radius * 1.08f
            fillPaint.color = if (i % 3 == 0) cyan else blue
            canvas.drawCircle(x, y, if (i % 3 == 0) 5f else 3f, fillPaint)
        }
    }

    private fun drawTexts(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        textPaint.color = cyan
        textPaint.textSize = 22f
        canvas.drawText(state.status, cx, cy - radius - 70f, textPaint)

        textPaint.color = white
        textPaint.textSize = 34f
        canvas.drawText(if (state.isListening) "LISTENING" else "JARVIS", cx, cy + 12f, textPaint)

        textPaint.textSize = 18f
        textPaint.color = Color.argb(210, 234, 251, 255)
        canvas.drawText("Tap core to toggle voice loop", cx, cy + radius + 55f, textPaint)

        textPaint.textSize = 17f
        textPaint.color = Color.argb(185, 234, 251, 255)
        canvas.drawText("Heard: ${state.transcript.take(44)}", cx, height - 130f, textPaint)
        canvas.drawText(state.response.take(54), cx, height - 95f, textPaint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
