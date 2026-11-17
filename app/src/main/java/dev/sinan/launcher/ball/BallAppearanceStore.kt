package dev.sinan.launcher.ball

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.sinan.launcher.R
import java.util.concurrent.Executors

enum class BallStyle(val label: Int) {
    CLASSIC(R.string.ball_style_classic), CURSOR(R.string.ball_style_cursor),
    POLARIS(R.string.ball_style_polaris), JADE(R.string.ball_style_jade), SEAL(R.string.ball_style_seal),
}

data class BallAppearance(val style: BallStyle = BallStyle.CLASSIC, val opacity: Int = 100, val size: Int = 48)

/** 主线程发布即时外观，后台串行持久化；滑动期间合并写入，不保存开启状态。 */
class BallAppearanceStore(context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("floating-ball-appearance", Context.MODE_PRIVATE) }
    private val listeners = mutableSetOf<() -> Unit>()
    var current = BallAppearance()
        private set
    var isReady = false
        private set
    private var dirty = false
    private val save = Runnable { flush() }

    init {
        worker.execute {
            val loaded = runCatching {
                val style = BallStyle.entries.firstOrNull { it.name == prefs.getString("style", "CLASSIC") } ?: BallStyle.CLASSIC
                BallAppearance(style, prefs.getInt("opacity", 100).coerceIn(20, 100), prefs.getInt("size", 48).coerceIn(32, 80))
            }.getOrDefault(BallAppearance())
            main.post { current = loaded; isReady = true; changed() }
        }
    }

    fun select(style: BallStyle) { update(current.copy(style = style)); flush() }
    fun opacity(value: Int) = update(current.copy(opacity = value.coerceIn(20, 100)))

    fun size(value: Int) = update(current.copy(size = value.coerceIn(32, 80)))

    private fun update(value: BallAppearance) {
        if (!isReady || value == current) return
        current = value
        dirty = true
        changed()
        main.removeCallbacks(save)
        main.postDelayed(save, 200)
    }

    fun flush() {
        main.removeCallbacks(save)
        if (!dirty) return
        dirty = false
        val snapshot = current
        worker.execute { prefs.edit().putString("style", snapshot.style.name).putInt("opacity", snapshot.opacity).putInt("size", snapshot.size).apply() }
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
}
