package dev.sinan.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import dev.sinan.launcher.appearance.AppearanceImage
import dev.sinan.launcher.appearance.AppearanceImageDrawable
import dev.sinan.launcher.ui.AppGridView
import dev.sinan.launcher.ui.SettingsActions
import dev.sinan.launcher.ui.SettingsPage
import dev.sinan.launcher.ui.dp
import dev.sinan.launcher.ui.extractSystemInsets
import dev.sinan.launcher.ui.roundedShape

/** 透明前台宿主管理短生命周期的真实悬浮窗口，不启动常驻服务。 */
class PanelActivity : Activity() {
    private var panel: Dialog? = null
    private val layoutStore get() = (application as SinanApplication).layoutPreferences
    private val images get() = (application as SinanApplication).appearanceImages
    private var appliedBackground: Drawable? = null
    private var appliedImage: Bitmap? = null
    private val layoutObserver: () -> Unit = { applyLayoutPreferences() }
    private val imageObserver: () -> Unit = { applyPanelBackground() }

    private fun applyLayoutPreferences() {
        applyPanelBackground()
        if (panel?.isShowing == true) resizePanel()
    }

    private fun applyPanelBackground() {
        val window = panel?.window ?: return
        val image = images[AppearanceImage.PANEL]
        if (appliedBackground == null || appliedImage !== image) {
            appliedImage = image
            appliedBackground = if (image == null) getDrawable(R.drawable.panel_background)?.mutate()
                else AppearanceImageDrawable(this, image, AppearanceImage.PANEL)
            window.setBackgroundDrawable(appliedBackground)
        }
        appliedBackground?.alpha = layoutStore.current.panelOpacity * 255 / 100
    }
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
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val handleBar = FrameLayout(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setPadding(0, dp(2), 0, dp(8))
            addView(View(this@PanelActivity).apply {
                background = roundedShape(fillColor = getColor(R.color.handle_bar), radiusDp = 2)
            }, FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(handleBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        settingsActions = SettingsActions(
            this,
            overlay = true,
            onUsageCleared = { grid.refreshUsage() },
            onChanged = { if (::settingsPage.isInitialized) settingsPage.refresh() },
            beforeSystemUi = { systemUiPending = true; dialog.hide() },
            onSystemUiFinished = { systemUiPending = false; if (!isFinishing) showPanel() },
        )
        val pages = FrameLayout(this)
        grid = AppGridView(this, onSettings = { setSettingsPage(true, animate = true) }, onLaunched = { dialog.dismiss() })
        settingsPage = SettingsPage(this, onBack = { setSettingsPage(false, animate = true) }, actions = settingsActions)
        pages.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        pages.addView(settingsPage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(pages, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setSettingsPage(savedInstanceState?.getBoolean("settings-page") ?: intent.getBooleanExtra(OPEN_SETTINGS, false), animate = false)

        root.setOnApplyWindowInsetsListener { view, insets ->
            val edges = extractSystemInsets(insets)
            view.setPadding(
                dp(16) + edges.left,
                dp(8),
                dp(16) + edges.right,
                dp(12) + edges.bottom,
            )
            insets
        }

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
            addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setDimAmount(0.32f)
        }
        panel = dialog
        layoutStore.observe(layoutObserver)
        images.observe(imageObserver)
        applyLayoutPreferences()
        root.requestApplyInsets()
    }

    private var cancelBallRestore: () -> Unit = {}

    override fun onPause() {
        cancelBallRestore()
        if (::settingsActions.isInitialized) settingsActions.cancelUpdateCheck()
        super.onPause()
    }

    override fun onPostResume() {
        super.onPostResume()
        cancelBallRestore()
        cancelBallRestore = (application as SinanApplication).ball.restoreWhenReady()
        if (::settingsPage.isInitialized) settingsPage.refresh()
        systemUiPending = false
        showPanel()
        if (panel?.isShowing == true) settingsActions.checkForUpdates()
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
        layoutStore.removeObserver(layoutObserver)
        images.removeObserver(imageObserver)
        layoutStore.flush()
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
        if (intent.getBooleanExtra(OPEN_SETTINGS, false)) setSettingsPage(true, animate = false)
    }

    private fun navigateBack() {
        if (settingsActions.dismissTopDialog()) return
        if (showingSettings) setSettingsPage(false, animate = true) else panel?.dismiss()
    }

    private fun setSettingsPage(show: Boolean, animate: Boolean) {
        showingSettings = show
        grid.animate().cancel()
        settingsPage.animate().cancel()
        val incoming: View = if (show) settingsPage else grid
        val outgoing: View = if (show) grid else settingsPage
        outgoing.visibility = View.GONE
        outgoing.alpha = 1f
        incoming.visibility = View.VISIBLE
        if (animate && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            incoming.alpha = 0f
            incoming.animate().alpha(1f).setDuration(90).start()
        } else {
            incoming.alpha = 1f
        }
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

    @Deprecated("Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::settingsActions.isInitialized) settingsActions.onAppearanceImageResult(requestCode, resultCode, data)
    }

    private fun fallback() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.SHOW_SETTINGS, true))
        finish()
    }

    private fun stopWatchingDisplay() {
        if (watchingDisplay) getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        watchingDisplay = false
    }

    companion object {
        const val OPEN_SETTINGS = "dev.sinan.launcher.OPEN_PANEL_SETTINGS"
    }

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
        val value = layoutStore.current
        val width = (minOf(bounds.width(), dp(560)) * value.panelWidth / 100)
            .coerceIn(minOf(dp(320), bounds.width()), bounds.width())
        val maxHeight = (bounds.height() * 0.95f).toInt()
        val height = (bounds.height() * (if (landscape) 0.90f else 0.79f) * value.panelHeight / 100).toInt()
            .coerceIn(minOf(dp(if (landscape) 320 else 520), maxHeight), maxHeight)
        panel?.window?.let { if (it.attributes.width != width || it.attributes.height != height) it.setLayout(width, height) }
    }
}
