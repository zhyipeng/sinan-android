package dev.sinan.launcher.ball

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import dev.sinan.launcher.MainActivity
import dev.sinan.launcher.PanelActivity
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.ui.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** 用户开启的可见入口；不使用定时保活、闹钟、开机接收器或粘性重启。 */
class FloatingBallService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val controller get() = (application as SinanApplication).ball
    private val appearance get() = (application as SinanApplication).ballAppearance
    private var appliedStyle: BallStyle? = null
    private val appearanceObserver: () -> Unit = { applyAppearance(); reconcile() }
    private val positions get() = (application as SinanApplication).ballPositions
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var ball: ImageButton? = null
    private var attached = false
    private var started = false
    private var position = BallPositionStore.Position()
    private var positionRevision = 0
    private var safe = Rect()
    private var insets: WindowInsets? = null
    private var dragging = false
    private var canceled = false
    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var originRevision = 0
    // x 是物理显示坐标；左右贴边不随界面文字方向翻转。
    private val params by lazy { createParams() }
    @SuppressLint("RtlHardcoded")
    private fun createParams() =
        WindowManager.LayoutParams(dp(48), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            title = "SinanFloatingBall"
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
    private val observer: () -> Unit = { reconcile() }
    private val appOpsListener = AppOpsManager.OnOpChangedListener { _, _ -> main.post { reconcile() } }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { reconcile() }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (started) { dragging = false; canceled = true; positionRevision++; place(); reconcile() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !controller.isEnabled || !Settings.canDrawOverlays(this) || !notificationsAvailable(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) { reconcile(); return START_NOT_STICKY }
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID, notification)
            started = true
            controller.observe(observer)
            getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, appOpsListener)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
                if (Build.VERSION.SDK_INT >= 28) {
                    addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                }
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, main)
            createBall()
            appearance.observe(appearanceObserver)
            applyAppearance()
            val revision = positionRevision
            positions.load {
                if (started && revision == positionRevision) { position = it; place() }
            }
            reconcile()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.ball_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        ensureChannel(this)
        fun activity(code: Int, settings: Boolean) = PendingIntent.getActivity(this, code,
            (if (settings) Intent(this, PanelActivity::class.java).putExtra(PanelActivity.OPEN_SETTINGS, true)
             else Intent(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ball_notification)
            .setContentTitle(getString(R.string.ball_notification_title))
            .setContentText(getString(R.string.ball_notification_text))
            .setContentIntent(activity(1, false))
            .addAction(Notification.Action.Builder(null, getString(R.string.settings), activity(2, true)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.ball_disable), stop).build())
            .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .build()
    }

    private fun createBall() {
        ball = ImageButton(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            background = null
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.ball_description)
            setOnClickListener { openPanel() }
            setOnTouchListener { view, event ->
                if (touch(event)) view.performClick()
                true
            }
            setOnApplyWindowInsetsListener { _, value ->
                insets = value
                main.post { if (started && !dragging) place() }
                value
            }
        }
        place()
    }

    private fun applyAppearance() {
        val value = appearance.current
        if (appliedStyle != value.style) {
            appliedStyle = value.style
            ball?.setImageDrawable(BallStyleDrawable(this, value.style))
        }
        params.alpha = value.opacity / 100f
        update()
    }

    /** 返回是否形成点击；消费触摸与无障碍 performClick 在监听器中统一处理。 */
    private fun touch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                positionRevision++
                originRevision = positionRevision
                downX = event.rawX; downY = event.rawY
                originX = params.x; originY = params.y
                dragging = false; canceled = false
                ball?.isPressed = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> { canceled = true; ball?.isPressed = false }
            MotionEvent.ACTION_MOVE -> {
                if (canceled) return false
                val dx = event.rawX - downX; val dy = event.rawY - downY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(dx) > slop || abs(dy) > slop) dragging = true
                if (dragging) {
                    ball?.isPressed = false
                    params.x = (originX + dx).roundToInt().coerceIn(safe.left, safe.right)
                    params.y = (originY + dy).roundToInt().coerceIn(safe.top, safe.bottom)
                    update()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ball?.isPressed = false
                val click = !dragging && !canceled && event.actionMasked == MotionEvent.ACTION_UP && originRevision == positionRevision
                if (dragging && originRevision == positionRevision) snap()
                dragging = false
                return click
            }
        }
        return false
    }

    private fun snap() {
        val right = params.x - safe.left >= (safe.right - safe.left) / 2
        val fraction = if (safe.bottom > safe.top) (params.y - safe.top).toFloat() / (safe.bottom - safe.top) else position.fraction
        position = BallPositionStore.Position(right, fraction.coerceIn(0f, 1f))
        positions.save(position)
        place()
    }

    private fun place() {
        val size: Rect
        val edges: Rect?
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.maximumWindowMetrics
            size = metrics.bounds
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            edges = Rect(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            val display = windows.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            size = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            edges = null
        }
        @Suppress("DEPRECATION")
        val left = edges?.left ?: insets?.stableInsetLeft ?: 0
        @Suppress("DEPRECATION")
        val top = edges?.top ?: insets?.stableInsetTop ?: 0
        @Suppress("DEPRECATION")
        val right = edges?.right ?: insets?.stableInsetRight ?: 0
        @Suppress("DEPRECATION")
        val bottom = edges?.bottom ?: insets?.stableInsetBottom ?: 0
        val margin = dp(4)
        val minX = left + margin
        val minY = top + margin
        safe = Rect(minX, minY, maxOf(minX, size.width() - right - params.width - margin), maxOf(minY, size.height() - bottom - params.height - margin))
        params.x = if (position.right) safe.right else safe.left
        params.y = safe.top + ((safe.bottom - safe.top) * position.fraction).roundToInt()
        update()
    }

    private fun update() {
        if (!attached) return
        try { windows.updateViewLayout(ball, params) } catch (_: RuntimeException) { stopSelf() }
    }

    private fun reconcile() {
        if (!started) return
        if (!controller.isEnabled || !Settings.canDrawOverlays(this) || !notificationsAvailable(this)) { stopSelf(); return }
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val show = appearance.isReady && !controller.isPanelVisible && !locked && interactive
        if (!show && attached) detach()
        else if (show && !attached) {
            try { windows.addView(ball, params); attached = true } catch (_: RuntimeException) { stopSelf() }
        }
    }

    private fun detach() {
        if (!attached) return
        attached = false
        ball?.isPressed = false
        dragging = false; canceled = true
        runCatching { windows.removeViewImmediate(ball) }
    }

    private fun openPanel() {
        if (!attached || !Settings.canDrawOverlays(this) || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        try {
            // 在用户点击可见球时启动；保留球直至面板真正显示，避免用隐藏窗口伪造启动条件。
            startActivity(Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (_: RuntimeException) { Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_LONG).show() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (started) { dragging = false; canceled = true; positionRevision++; place(); reconcile() }
    }

    override fun onDestroy() {
        started = false
        main.removeCallbacksAndMessages(null)
        controller.removeObserver(observer)
        appearance.removeObserver(appearanceObserver)
        appearance.flush()
        runCatching { getSystemService(AppOpsManager::class.java).stopWatchingMode(appOpsListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        detach()
        ball = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        controller.stopped()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "floating-ball"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.sinan.launcher.ball.STOP"
        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.ball_channel), NotificationManager.IMPORTANCE_LOW))
        }
        fun notificationsAvailable(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
