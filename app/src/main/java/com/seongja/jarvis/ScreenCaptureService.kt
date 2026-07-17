package com.seongja.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val processingFrame = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startCaptureForeground()
        handlerThread = HandlerThread("friday-screen-capture").also { it.start() }
        captureHandler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProjection(intent)
            ACTION_STOP -> stopCapture("OWNER STOP")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseProjection()
        handlerThread?.quitSafely()
        handlerThread = null
        captureHandler = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun startProjection(intent: Intent) {
        if (projection != null) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.CANCELED)
        val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        if (resultCode != ActivityResultCodes.OK || resultData == null) {
            ScreenVisionRuntime.markError("Android screen-capture permission was not granted.")
            stopSelf()
            return
        }

        runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
                ?: error("MediaProjectionManager unavailable")
            val activeProjection = manager.getMediaProjection(resultCode, resultData)
                ?: error("Android rejected the screen-capture session")
            projection = activeProjection
            activeProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopCapture("ANDROID ENDED CAPTURE")
                    }
                },
                captureHandler
            )
            createVirtualDisplay(activeProjection)
            ScreenVisionRuntime.markCaptureStarted(this)
            FloatingHelixService.show(this, "VISUAL LINK ACTIVE")
        }.onFailure { error ->
            ScreenVisionRuntime.markError(error.message ?: error.javaClass.simpleName)
            stopSelf()
        }
    }

    private fun createVirtualDisplay(activeProjection: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi.coerceAtLeast(1)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            if (!processingFrame.compareAndSet(false, true)) {
                source.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            val image = source.acquireLatestImage()
            if (image == null) {
                processingFrame.set(false)
                return@setOnImageAvailableListener
            }
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val paddedWidth = image.width + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                if (cropped !== bitmap) bitmap.recycle()
                val stream = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, FRAME_JPEG_QUALITY, stream)
                cropped.recycle()
                ScreenVisionRuntime.updateFrame(stream.toByteArray())
            } catch (error: Throwable) {
                ScreenVisionRuntime.markError(error.message ?: "Screen frame conversion failed")
            } finally {
                image.close()
                processingFrame.set(false)
            }
        }, captureHandler)

        virtualDisplay = activeProjection.createVirtualDisplay(
            "FRIDAY Screen Vision",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
    }

    private fun stopCapture(reason: String) {
        releaseProjection()
        ScreenVisionRuntime.clear(this)
        FloatingHelixService.hide(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjection() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }

    private fun startCaptureForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val stopPending = android.app.PendingIntent.getService(
            this,
            0,
            stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("F.R.I.D.A.Y. visual channel")
            .setContentText("Screen sharing is active. Tap Stop to close the visual channel.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "F.R.I.D.A.Y. screen vision",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible status while F.R.I.D.A.Y. is sharing the screen"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "friday_screen_vision"
        private const val NOTIFICATION_ID = 9132
        private const val EXTRA_RESULT_CODE = "friday_projection_result_code"
        private const val EXTRA_RESULT_DATA = "friday_projection_result_data"
        private const val FRAME_JPEG_QUALITY = 72
        private const val ACTION_START = "com.seongja.jarvis.START_SCREEN_CAPTURE"
        private const val ACTION_STOP = "com.seongja.jarvis.STOP_SCREEN_CAPTURE"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

private object ActivityResultCodes {
    const val OK = Activity.RESULT_OK
    const val CANCELED = Activity.RESULT_CANCELED
}
