package dev.sinan.launcher.ball

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.sinan.launcher.R
import java.util.concurrent.Executors

/** 主线程协调用户意愿；偏好读写在后台串行执行，服务销毁不清除开启意愿。 */
class FloatingBallController(private val context: Context) {
    var isEnabled = false
        private set
    var isReady = false
        private set
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("floating-ball-state", Context.MODE_PRIVATE) }
    private var revision = 0
    private val panels = mutableSetOf<Any>()
    private val listeners = mutableSetOf<() -> Unit>()
    val isPanelVisible get() = panels.isNotEmpty()

    init {
        worker.execute {
            val enabled = runCatching { prefs.getBoolean("enabled", false) }.getOrDefault(false)
            main.post {
                if (revision == 0) isEnabled = enabled
                isReady = true
                changed()
            }
        }
    }

    fun enable() {
        val request = ++revision
        isEnabled = true
        changed()
        // 先落盘再启动，保证服务首次出现后被杀也能恢复。
        worker.execute {
            val saved = runCatching { prefs.edit().putBoolean("enabled", true).commit() }.getOrDefault(false)
            main.post {
                if (request != revision) return@post
                if (!saved) { failed(); return@post }
                restore()
            }
        }
    }

    /** 仅供前台用户操作或系统开机广播调用；不从 Application 后台自动启动。 */
    fun restore() {
        if (!isReady || !isEnabled) return
        try {
            context.startForegroundService(Intent(context, FloatingBallService::class.java))
        } catch (_: IllegalStateException) {
            Toast.makeText(context, R.string.ball_failed, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.ball_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** 前台宿主等待偏好读取完成；离开前台时取消，避免后台拉起。 */
    fun restoreWhenReady(): () -> Unit {
        val listener = object : () -> Unit {
            override fun invoke() {
                if (!isReady) return
                removeObserver(this)
                if (android.provider.Settings.canDrawOverlays(context)) restore()
            }
        }
        observe(listener)
        listener()
        return { removeObserver(listener) }
    }

    fun disable() {
        revision++
        isEnabled = false
        worker.execute { runCatching { prefs.edit().putBoolean("enabled", false).commit() } }
        context.stopService(Intent(context, FloatingBallService::class.java))
        changed()
    }

    fun stopped() { changed() }
    fun panelShown(owner: Any) { panels.add(owner); changed() }
    fun panelHidden(owner: Any) { panels.remove(owner); changed() }
    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }

    private fun failed() {
        disable()
        Toast.makeText(context, R.string.ball_failed, Toast.LENGTH_LONG).show()
    }
}
