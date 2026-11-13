package dev.sinan.launcher.ball

import android.content.Context
import android.content.Intent
import dev.sinan.launcher.R
import android.widget.Toast

/** 仅主线程使用；开启状态不持久化，不在进程/设备重启后擅自恢复常驻服务。 */
class FloatingBallController(private val context: Context) {
    var isEnabled = false
        private set
    private val panels = mutableSetOf<Any>()
    private val listeners = mutableSetOf<() -> Unit>()
    val isPanelVisible get() = panels.isNotEmpty()

    fun enable() {
        if (isEnabled) return
        isEnabled = true
        try {
            context.startForegroundService(Intent(context, FloatingBallService::class.java))
        } catch (_: IllegalStateException) {
            failed()
        } catch (_: SecurityException) {
            failed()
        }
    }

    fun disable() {
        isEnabled = false
        context.stopService(Intent(context, FloatingBallService::class.java))
        changed()
    }

    fun stopped() {
        isEnabled = false
        changed()
    }

    fun panelShown(owner: Any) { panels.add(owner); changed() }
    fun panelHidden(owner: Any) { panels.remove(owner); changed() }
    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }

    private fun failed() {
        isEnabled = false
        Toast.makeText(context, R.string.ball_failed, Toast.LENGTH_LONG).show()
        changed()
    }
}
