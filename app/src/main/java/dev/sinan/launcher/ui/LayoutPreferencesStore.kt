package dev.sinan.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

data class LayoutPreferences(
    val panelWidth: Int = 100,
    val panelHeight: Int = 100,
    val panelOpacity: Int = 100,
    val iconSize: Int = 48,
    val columns: Int = 0,
    val keyboardSize: Int = 100,
)

/** 主线程只发布内存快照，加载和合并写入均在后台串行完成。 */
class LayoutPreferencesStore(context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("layout-preferences", Context.MODE_PRIVATE) }
    private val listeners = mutableSetOf<() -> Unit>()
    var current = LayoutPreferences()
        private set
    var isReady = false
        private set
    private var dirty = false
    private val save = Runnable { flush() }

    init {
        worker.execute {
            val loaded = runCatching {
                LayoutPreferences(
                    prefs.getInt("panel-width", 100).coerceIn(70, 120),
                    prefs.getInt("panel-height", 100).coerceIn(75, 120),
                    prefs.getInt("panel-opacity", 100).coerceIn(20, 100),
                    prefs.getInt("icon-size", 48).coerceIn(32, 72),
                    prefs.getInt("columns", 0).let { if (it in 2..6) it else 0 },
                    prefs.getInt("keyboard-size", 100).coerceIn(80, 130),
                )
            }.getOrDefault(LayoutPreferences())
            main.post { current = loaded; isReady = true; changed() }
        }
    }

    fun update(value: LayoutPreferences) {
        if (!isReady) return
        val valid = value.copy(
            panelWidth = value.panelWidth.coerceIn(70, 120),
            panelHeight = value.panelHeight.coerceIn(75, 120),
            panelOpacity = value.panelOpacity.coerceIn(20, 100),
            iconSize = value.iconSize.coerceIn(32, 72),
            columns = if (value.columns in 2..6) value.columns else 0,
            keyboardSize = value.keyboardSize.coerceIn(80, 130),
        )
        if (valid == current) return
        current = valid
        dirty = true
        changed()
        main.removeCallbacks(save)
        main.postDelayed(save, 200)
    }

    fun flush() {
        main.removeCallbacks(save)
        if (!dirty) return
        dirty = false
        val value = current
        worker.execute {
            prefs.edit().putInt("panel-width", value.panelWidth).putInt("panel-height", value.panelHeight)
                .putInt("panel-opacity", value.panelOpacity).putInt("icon-size", value.iconSize)
                .putInt("columns", value.columns).putInt("keyboard-size", value.keyboardSize).apply()
        }
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
}
