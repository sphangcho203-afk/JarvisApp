package com.seongja.jarvis

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingHelixService : Service() {
    private lateinit var windowManager: WindowManager
    private var root: LinearLayout? = null
    private var statusView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> stopSelf()
            ACTION_SHOW -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val text = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "STANDING BY" }
                showOrUpdate(text)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        statusView = null
        params = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOrUpdate(status: String) {
        statusView?.text = status.take(52)
        if (root != null) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(236, 2, 10, 22))
                setStroke(dp(1), Color.rgb(90, 195, 255))
            }
            elevation = dp(12).toFloat()
        }

        val core = TextView(this).apply {
            text = "H"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.rgb(128, 225, 255))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(4, 22, 43))
                setStroke(dp(2), Color.rgb(88, 173, 255))
            }
        }
        container.addView(core, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        statusView = TextView(this).apply {
            text = status.take(52)
            gravity = Gravity.CENTER
            textSize = 6.5f
            letterSpacing = .08f
            setTextColor(Color.rgb(187, 221, 242))
            maxLines = 2
        }
        container.addView(statusView, LinearLayout.LayoutParams(dp(86), dp(27)).apply {
            topMargin = dp(4)
        })

        val stop = TextView(this).apply {
            text = "STOP"
            gravity = Gravity.CENTER
            textSize = 7f
            letterSpacing = .08f
            setTextColor(Color.rgb(255, 128, 138))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { FridaySessionController.sleep(applicationContext, "FLOATING CONTROL") }
        }
        container.addView(stop, LinearLayout.LayoutParams(dp(62), dp(24)))

        val overlayParams = WindowManager.LayoutParams(
            dp(104),
            dp(118),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(140)
        }

        attachDrag(container, overlayParams)
        container.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        root = container
        params = overlayParams
        windowManager.addView(container, overlayParams)
    }

    private fun attachDrag(view: View, layout: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) dragging = true
                    layout.x = (startX + dx).coerceAtLeast(0)
                    layout.y = (startY + dy).coerceAtLeast(0)
                    root?.let { runCatching { windowManager.updateViewLayout(it, layout) } }
                    true
                }
                MotionEvent.ACTION_UP -> !dragging
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ACTION_SHOW = "com.seongja.jarvis.SHOW_FLOATING_HELIX"
        private const val ACTION_HIDE = "com.seongja.jarvis.HIDE_FLOATING_HELIX"
        private const val EXTRA_STATUS = "friday_helix_status"

        fun show(context: Context, status: String = "STANDING BY") {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(
                Intent(context, FloatingHelixService::class.java)
                    .setAction(ACTION_SHOW)
                    .putExtra(EXTRA_STATUS, status)
            )
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, FloatingHelixService::class.java).setAction(ACTION_HIDE)
            )
        }
    }
}
