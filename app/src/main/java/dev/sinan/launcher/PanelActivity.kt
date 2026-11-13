package dev.sinan.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import dev.sinan.launcher.ui.AppGridView
import dev.sinan.launcher.ui.dp
import dev.sinan.launcher.ui.heading

/** 透明前台宿主管理短生命周期的真实悬浮窗口，不启动常驻服务。 */
class PanelActivity : Activity() {
    private var panel: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            fallback()
            return
        }
        val dialog = Dialog(this, R.style.Theme_Sinan_Panel)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(heading().apply { setText(R.string.app_name) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            setText(R.string.settings)
            setOnClickListener { fallback() }
        })
        header.addView(Button(this).apply {
            setText(R.string.close)
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)
        root.addView(AppGridView(this) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setDimAmount(0.28f)
        }
        panel = dialog
    }

    override fun onPostResume() {
        super.onPostResume()
        val dialog = panel ?: return
        if (dialog.isShowing) return
        try {
            if (!Settings.canDrawOverlays(this)) {
                fallback()
                return
            }
            dialog.show()
            val metrics = resources.displayMetrics
            dialog.window?.setLayout(minOf(metrics.widthPixels, dp(560)), (metrics.heightPixels * 0.66f).toInt())
        } catch (_: SecurityException) {
            fallback()
        } catch (_: WindowManager.BadTokenException) {
            Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_LONG).show()
            fallback()
        }
    }

    override fun onStop() {
        if (isChangingConfigurations) panel?.setOnDismissListener(null)
        panel?.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        panel?.setOnDismissListener(null)
        panel?.dismiss()
        panel = null
        super.onDestroy()
    }

    private fun fallback() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.SHOW_SETTINGS, true))
        finish()
    }
}
