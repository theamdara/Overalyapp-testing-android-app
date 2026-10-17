package com.example.overlayblock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * Draws an opaque, touch-blocking window on top of other apps.
 * The user can drag the block to move it, and drag any edge or corner
 * (the subtle grips hint at them) to resize it on the fly.
 * The block color is changeable from MainActivity and persisted.
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_control"
        const val NOTIFICATION_ID = 1001
        private const val MIN_SIZE_DP = 64
        private const val EDGE_ZONE_DP = 28
        const val PREFS = "overlay_prefs"
        const val KEY_COLOR = "block_color"
        const val EXTRA_COLOR = "extra_color"
        const val DEFAULT_COLOR = Color.BLACK

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var blockColor: Int = DEFAULT_COLOR

    // Which edges a gesture should resize; corners combine two.
    private class ResizeSpec(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        blockColor = prefs.getInt(KEY_COLOR, DEFAULT_COLOR)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("NewApi")
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        addOverlay()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Allow MainActivity to push a new color while running.
        intent?.getIntExtra(EXTRA_COLOR, -1)?.let { c ->
            if (c != -1) setColor(c)
        }
        return START_STICKY
    }

    /** If the user swipes our task away, also remove the overlay. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    /** Change the block color live and persist it. */
    fun setColor(color: Int) {
        blockColor = color
        prefs.edit().putInt(KEY_COLOR, color).apply()
        applyColors()
    }

    // region overlay window

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        if (overlayView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)
        // Start at half the screen so the block is immediately obvious.
        val w = screenW() / 2
        val h = screenH() / 2
        val p = WindowManager.LayoutParams(
            w,
            h,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (screenW() - w) / 2
        p.y = (screenH() - h) / 2

        windowManager.addView(view, p)
        overlayView = view
        params = p

        applyColors()
        installGestureHandler(view)

        // HONOR (and some other OEMs) hide non-system overlay windows when the
        // owning app goes to the background. A periodic re-assert of the layout
        // keeps the window in the visible set.
        keepAliveHandler.postDelayed(keepAliveRunnable, 2000)
    }

    private val keepAliveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            val v = overlayView
            val p = params
            if (v != null && p != null) {
                runCatching { windowManager.updateViewLayout(v, p) }
            }
            keepAliveHandler.postDelayed(this, 2000)
        }
    }

    private fun removeOverlay() {
        keepAliveHandler.removeCallbacks(keepAliveRunnable)
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
        params = null
    }

    /** TYPE_APPLICATION_OVERLAY on API 26+, TYPE_PHONE below. */
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    /** Paint the opaque surface and tint the blended-in corner grips. */
    private fun applyColors() {
        val view = overlayView ?: return
        view.findViewById<View>(R.id.blockSurface).setBackgroundColor(blockColor)
        val gripTint = lighten(blockColor, 0.35f)
        listOf(
            R.id.handleTopLeft, R.id.handleTopRight,
            R.id.handleBottomLeft, R.id.handleBottomRight
        ).forEach { id ->
            view.findViewById<View>(id).background?.mutate()?.setTint(gripTint)
        }
    }

    // endregion

    // region gestures (move + edge/corner resize in one handler)

    private enum class Zone { NONE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private fun zoneFor(x: Float, y: Float, v: View): Zone {
        val edge = dpToPx(EDGE_ZONE_DP).toFloat()
        val onLeft = x < edge
        val onRight = x > v.width - edge
        val onTop = y < edge
        val onBottom = y > v.height - edge
        return when {
            onTop && onLeft -> Zone.TOP_LEFT
            onTop && onRight -> Zone.TOP_RIGHT
            onBottom && onLeft -> Zone.BOTTOM_LEFT
            onBottom && onRight -> Zone.BOTTOM_RIGHT
            onLeft -> Zone.LEFT
            onRight -> Zone.RIGHT
            onTop -> Zone.TOP
            onBottom -> Zone.BOTTOM
            else -> Zone.NONE
        }
    }

    private fun specFor(zone: Zone): ResizeSpec? = when (zone) {
        Zone.NONE -> null
        Zone.LEFT -> ResizeSpec(true, false, false, false)
        Zone.RIGHT -> ResizeSpec(false, true, false, false)
        Zone.TOP -> ResizeSpec(false, false, true, false)
        Zone.BOTTOM -> ResizeSpec(false, false, false, true)
        Zone.TOP_LEFT -> ResizeSpec(true, false, true, false)
        Zone.TOP_RIGHT -> ResizeSpec(false, true, true, false)
        Zone.BOTTOM_LEFT -> ResizeSpec(true, false, false, true)
        Zone.BOTTOM_RIGHT -> ResizeSpec(false, true, false, true)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installGestureHandler(root: View) {
        root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    val p = params ?: return@setOnTouchListener true
                    origLeft = p.x
                    origTop = p.y
                    origWidth = p.width
                    origHeight = p.height
                    activeSpec = specFor(zoneFor(event.x, event.y, v))
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = params ?: return@setOnTouchListener true
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    when (val spec = activeSpec) {
                        null -> {
                            // Move the whole block.
                            val newX = (origLeft + dx).coerceIn(0, screenW() - p.width)
                            val newY = (origTop + dy).coerceIn(0, screenH() - p.height)
                            p.x = newX
                            p.y = newY
                        }
                        else -> applyResize(spec, dx, dy, p)
                    }
                    windowManager.updateViewLayout(v, p)
                    true
                }
                else -> true
            }
        }
    }

    private fun applyResize(spec: ResizeSpec, dx: Int, dy: Int, p: WindowManager.LayoutParams) {
        val min = dpToPx(MIN_SIZE_DP)
        val maxX = screenW()
        val maxY = screenH()

        var left = origLeft
        var top = origTop
        var width = origWidth
        var height = origHeight

        if (spec.left) {
            left = (origLeft + dx).coerceAtMost(origLeft + origWidth - min)
            width = origLeft + origWidth - left
        }
        if (spec.right) {
            width = (origWidth + dx).coerceIn(min, maxX - origLeft)
        }
        if (spec.top) {
            top = (origTop + dy).coerceAtMost(origTop + origHeight - min)
            height = origTop + origHeight - top
        }
        if (spec.bottom) {
            height = (origHeight + dy).coerceIn(min, maxY - origTop)
        }

        p.x = left.coerceIn(0, maxX - min)
        p.y = top.coerceIn(0, maxY - min)
        p.width = width
        p.height = height
    }

    // endregion

    // region helpers

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var origLeft = 0
    private var origTop = 0
    private var origWidth = 0
    private var origHeight = 0
    private var activeSpec: ResizeSpec? = null

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels

    /** Mix the color toward white by [amount] (0..1) for the grip tint. */
    private fun lighten(color: Int, amount: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_description) }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    // endregion
}
