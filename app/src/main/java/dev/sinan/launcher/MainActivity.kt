package dev.sinan.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.sinan.launcher.ui.AppGridView
import dev.sinan.launcher.ui.dp
import dev.sinan.launcher.ui.heading

/** 无悬浮权限时直接使用网格；已有权限时桌面入口默认打开面板。 */
class MainActivity : Activity() {
    private lateinit var permission: Button
    private lateinit var description: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !intent.getBooleanExtra(SHOW_SETTINGS, false) && Settings.canDrawOverlays(this)) {
            openPanel()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(heading().apply { setText(R.string.app_name) })
        description = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(description)
        permission = Button(this).apply {
            isAllCaps = false
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) openPanel() else requestOverlay()
            }
        }
        root.addView(permission, LinearLayout.LayoutParams(-1, -2))
        root.addView(AppGridView(this) { finish() }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val edges = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                android.graphics.Rect(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                android.graphics.Rect(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            view.setPadding(dp(20) + edges.left, dp(16) + edges.top, dp(20) + edges.right, dp(16) + edges.bottom)
            insets
        }
        root.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        if (::permission.isInitialized) {
            val allowed = Settings.canDrawOverlays(this)
            permission.setText(if (allowed) R.string.open_panel else R.string.allow_overlay)
            description.setText(if (allowed) R.string.overlay_enabled else R.string.overlay_optional)
        }
    }

    private fun requestOverlay() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.permission_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openPanel() {
        startActivity(Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    companion object {
        const val SHOW_SETTINGS = "dev.sinan.launcher.SHOW_SETTINGS"
    }
}
