package dev.sinan.launcher.ball

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** 仅保存贴边方向及可用高度中的比例；所有偏好读写在后台串行执行。 */
class BallPositionStore(context: Context) {
    data class Position(val right: Boolean = true, val fraction: Float = 0.45f)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("floating-ball-position", Context.MODE_PRIVATE) }

    fun load(callback: (Position) -> Unit) {
        worker.execute {
            val position = runCatching {
                val fraction = prefs.getFloat("fraction", 0.45f).takeIf { it.isFinite() } ?: 0.45f
                Position(prefs.getBoolean("right", true), fraction.coerceIn(0f, 1f))
            }.getOrDefault(Position())
            main.post { callback(position) }
        }
    }

    fun save(position: Position) {
        worker.execute {
            prefs.edit().putBoolean("right", position.right).putFloat("fraction", position.fraction.coerceIn(0f, 1f)).apply()
        }
    }
}
