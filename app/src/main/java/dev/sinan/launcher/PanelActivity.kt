package dev.sinan.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.View
import android.view.KeyEvent
import dev.sinan.launcher.ui.SettingsActions
import dev.sinan.launcher.ui.SettingsPage
import android.widget.LinearLayout
import android.widget.Toast
import dev.sinan.launcher.ui.AppGridView
import dev.sinan.launcher.ui.dp

/** 透明前台宿主管理短生命周期的真实悬浮窗口，不启动常驻服务。 */
class PanelActivity : Activity() {
    private var panel: Dialog? = null
    private lateinit var grid: AppGridView
    private lateinit var settingsPage: SettingsPage
    private lateinit var settingsActions: SettingsActions
    private var showingSettings = false
    private var systemUiPending = false
    private var watchingDisplay = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (panel?.isShowing == true) resizePanel()
        }
    }

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
        settingsActions = SettingsActions(this, overlay = true,
            onUsageCleared = { grid.refreshUsage() },
            onChanged = { if (::settingsPage.isInitialized) settingsPage.refresh() },
            beforeSystemUi = { systemUiPending = true; dialog.hide() },
            onSystemUiFinished = { systemUiPending = false; if (!isFinishing) showPanel() })
        val pages = FrameLayout(this)
        grid = AppGridView(this, onSettings = { setSettingsPage(true) }, onLaunched = { dialog.dismiss() })
        settingsPage = SettingsPage(this, onBack = { setSettingsPage(false) }, actions = settingsActions)
        pages.addView(grid, FrameLayout.LayoutParams(-1, -1))
        pages.addView(settingsPage, FrameLayout.LayoutParams(-1, -1))
        root.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        setSettingsPage(savedInstanceState?.getBoolean("settings-page") ?: intent.getBooleanExtra(OPEN_SETTINGS, false))
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK && (showingSettings || settingsActions.hasDialogs)) {
                if (event.action == KeyEvent.ACTION_UP) navigateBack()
                true
            } else false
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                navigateBack()
            }
        }
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
        if (::settingsPage.isInitialized) settingsPage.refresh()
        systemUiPending = false
        showPanel()
    }

    private fun showPanel() {
        val dialog = panel ?: return
        try {
            if (!Settings.canDrawOverlays(this)) {
                fallback()
                return
            }
            dialog.show()
            resizePanel()
            (application as SinanApplication).ball.panelShown(this)
            if (!watchingDisplay) {
                getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
                watchingDisplay = true
            }
        } catch (_: SecurityException) {
            fallback()
        } catch (_: WindowManager.BadTokenException) {
            Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_LONG).show()
            fallback()
        }
    }

    override fun onStop() {
        stopWatchingDisplay()
        if (::settingsActions.isInitialized) settingsActions.dismissDialogs()
        (application as SinanApplication).ball.panelHidden(this)
        if (systemUiPending && !isChangingConfigurations) {
            panel?.hide()
            super.onStop()
            return
        }
        if (isChangingConfigurations) panel?.setOnDismissListener(null)
        panel?.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        if (::settingsActions.isInitialized) settingsActions.dismissDialogs()
        (application as SinanApplication).ball.panelHidden(this)
        stopWatchingDisplay()
        panel?.setOnDismissListener(null)
        panel?.dismiss()
        panel = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OPEN_SETTINGS, false)) setSettingsPage(true)
    }

    private fun navigateBack() {
        if (settingsActions.dismissTopDialog()) return
        if (showingSettings) setSettingsPage(false) else panel?.dismiss()
    }

    private fun setSettingsPage(show: Boolean) {
        showingSettings = show
        grid.visibility = if (show) View.GONE else View.VISIBLE
        settingsPage.visibility = if (show) View.VISIBLE else View.GONE
        if (show) settingsPage.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settings-page", showingSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::settingsActions.isInitialized) settingsActions.onPermissionResult(requestCode, grantResults)
    }

    private fun fallback() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.SHOW_SETTINGS, true))
        finish()
    }

    private fun stopWatchingDisplay() {
        if (watchingDisplay) getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        watchingDisplay = false
    }

    companion object { const val OPEN_SETTINGS = "dev.sinan.launcher.OPEN_PANEL_SETTINGS" }

    private fun resizePanel() {
        // 透明 Activity 在兼容模式下可能保持竖屏尺寸，使用显示区域边界并监听真实显示变化。
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) {
            applicationContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val display = applicationContext.getSystemService(WindowManager::class.java).defaultDisplay
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val landscape = bounds.width() > bounds.height()
        val width = minOf(bounds.width(), dp(560))
        val height = (bounds.height() * if (landscape) 0.90f else 0.78f).toInt()
        panel?.window?.let { if (it.attributes.width != width || it.attributes.height != height) it.setLayout(width, height) }
    }
}
