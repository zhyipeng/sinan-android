package dev.sinan.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import dev.sinan.launcher.ball.FloatingBallService
import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var grid: AppGridView
    private var notificationRequested = false

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
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(heading().apply { setText(R.string.app_name) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            setText(R.string.usage_records)
            setOnClickListener { showUsageRecords() }
        })
        root.addView(header)
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
        grid = AppGridView(this, onSettings = { showSettingsMenu() }, onLaunched = { finish() })
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
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

    private fun showSettingsMenu() {
        val ball = (application as SinanApplication).ball
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(arrayOf(getString(if (Settings.canDrawOverlays(this)) R.string.open_panel else R.string.allow_overlay), getString(R.string.usage_records), getString(if (ball.isEnabled) R.string.ball_disable else R.string.ball_enable))) { _, index ->
                when (index) {
                    0 -> if (Settings.canDrawOverlays(this)) openPanel() else requestOverlay()
                    1 -> showUsageRecords()
                    2 -> if (ball.isEnabled) ball.disable() else explainBall()
                }
            }.show()
    }

    private fun explainBall() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ball_enable)
            .setMessage(R.string.ball_explanation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ball_enable) { _, _ -> enableBall() }
            .show()
    }

    private fun enableBall() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.ball_overlay_needed, Toast.LENGTH_LONG).show()
            requestOverlay()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (notificationRequested && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationSettings()
            } else {
                // 同一设置会话内拒绝后可转到系统通知设置，不保存开启状态。
                notificationRequested = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), BALL_NOTIFICATION_REQUEST)
            }
            return
        }
        FloatingBallService.ensureChannel(this)
        if (!FloatingBallService.notificationsAvailable(this)) { notificationSettings(); return }
        (application as SinanApplication).ball.enable()
    }

    private fun notificationSettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.ball_notification_needed)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ball_notification_settings) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, R.string.ball_failed, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BALL_NOTIFICATION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && !isFinishing) enableBall()
            else Toast.makeText(this, R.string.ball_notification_needed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showUsageRecords() {
        AlertDialog.Builder(this)
            .setTitle(R.string.usage_records)
            .setMessage(R.string.usage_records_description)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.usage_clear) { _, _ ->
                (application as SinanApplication).apps.clearUsage { result ->
                    if (isFinishing || isDestroyed) return@clearUsage
                    result.fold(onSuccess = {
                        Toast.makeText(this, R.string.usage_cleared, Toast.LENGTH_SHORT).show()
                        grid.refreshUsage()
                    }, onFailure = { Toast.makeText(this, R.string.usage_clear_failed, Toast.LENGTH_LONG).show() })
                }
            }.show()
    }

    companion object {
        private const val BALL_NOTIFICATION_REQUEST = 210
        const val SHOW_SETTINGS = "dev.sinan.launcher.SHOW_SETTINGS"
    }
}
