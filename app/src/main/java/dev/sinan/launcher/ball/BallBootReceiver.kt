package dev.sinan.launcher.ball

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.sinan.launcher.SinanApplication

/** 解锁后的开机广播恢复用户已开启的入口；不处理强行停止或锁定启动。 */
class BallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val controller = (context.applicationContext as SinanApplication).ball
        fun restore() {
            try {
                if (Settings.canDrawOverlays(context)) controller.restore()
            } finally { pending.finish() }
        }
        if (controller.isReady) restore()
        else {
            val listener = object : () -> Unit {
                override fun invoke() {
                    if (!controller.isReady) return
                    controller.removeObserver(this)
                    restore()
                }
            }
            controller.observe(listener)
        }
    }
}
