package dev.sinan.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.sinan.launcher.ui.AppGridView
import dev.sinan.launcher.ui.SettingsActions
import dev.sinan.launcher.ui.applyPillCard
import dev.sinan.launcher.ui.attachPressScale
import dev.sinan.launcher.ui.dp
import dev.sinan.launcher.ui.dpF
import dev.sinan.launcher.ui.extractSystemInsets
import dev.sinan.launcher.ui.heading
import dev.sinan.launcher.ui.roundedShape
import dev.sinan.launcher.ui.styleActionButton

/** 无悬浮权限时直接使用网格；已有权限时桌面入口默认打开面板。 */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var permission: Button
    private lateinit var statusDot: View
    private lateinit var description: TextView
    private lateinit var statusRow: LinearLayout
    private lateinit var bannerCard: LinearLayout
    private lateinit var grid: AppGridView
    private var lastInsets = android.graphics.Rect()
    private val settingsActions by lazy { SettingsActions(this, onUsageCleared = { grid.refreshUsage() }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !intent.getBooleanExtra(SHOW_SETTINGS, false) && Settings.canDrawOverlays(this)) {
            openPanel()
            return
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 顶部品牌栏
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpF(9))
                }
            }
            clipToOutline = true
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
        header.addView(heading(21f).apply { setText(R.string.app_name) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val usageButton = Button(this).apply {
            setText(R.string.usage_records)
            styleActionButton(
                button = this,
                fillColorRes = R.color.surface_elevated,
                textColorRes = R.color.text_primary,
                strokeColorRes = R.color.divider_subtle,
                radiusDp = 18,
                textSizeSp = 13f,
                horizontalPaddingDp = 14,
                verticalPaddingDp = 7,
            )
            attachPressScale(0.96f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                settingsActions.showUsageRecords()
            }
        }
        header.addView(usageButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(header)

        // 权限状态引导卡片
        bannerCard = LinearLayout(this).apply {
            applyPillCard(this, radiusDp = 16, fillColorRes = R.color.surface_elevated, strokeColorRes = R.color.divider_subtle)
        }
        statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            marginEnd = dp(8)
        })
        description = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setLineSpacing(dpF(2), 1f)
        }
        statusRow.addView(description, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bannerCard.addView(statusRow)

        permission = Button(this).apply {
            styleActionButton(
                button = this,
                fillColorRes = R.color.brand_accent,
                textColorRes = R.color.brand_accent_on,
                rippleColorRes = R.color.ripple_on_accent,
                radiusDp = 12,
                textSizeSp = 14f,
                horizontalPaddingDp = 16,
                verticalPaddingDp = 9,
            )
            attachPressScale(0.98f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (Settings.canDrawOverlays(this@MainActivity)) openPanel() else requestOverlay()
            }
        }
        bannerCard.addView(permission)
        root.addView(bannerCard)

        grid = AppGridView(this, onSettings = { showSettingsMenu() }, onLaunched = { finish() })
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        applyOrientationLayout(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        root.setOnApplyWindowInsetsListener { _, insets ->
            lastInsets = extractSystemInsets(insets)
            applyRootPadding(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            insets
        }
        root.requestApplyInsets()
    }

    private fun applyRootPadding(landscape: Boolean) {
        val hPad = dp(if (landscape) 16 else 18)
        val vPad = dp(if (landscape) 8 else 14)
        root.setPadding(
            hPad + lastInsets.left,
            vPad + lastInsets.top,
            hPad + lastInsets.right,
            dp(if (landscape) 8 else 12) + lastInsets.bottom,
        )
    }

    private fun applyOrientationLayout(landscape: Boolean) {
        applyRootPadding(landscape)
        header.setPadding(0, 0, 0, dp(if (landscape) 8 else 12))
        bannerCard.apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (landscape) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            setPadding(dp(14), dp(if (landscape) 8 else 12), dp(14), dp(if (landscape) 8 else 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(if (landscape) 6 else 8)
            }
        }
        statusRow.apply {
            setPadding(0, 0, dp(if (landscape) 12 else 0), dp(if (landscape) 0 else 10))
            layoutParams = if (landscape) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
        permission.layoutParams = if (landscape) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::root.isInitialized) {
            applyOrientationLayout(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getBooleanExtra(SHOW_SETTINGS, false) && Settings.canDrawOverlays(this)) openPanel()
    }

    override fun onResume() {
        super.onResume()
        if (::permission.isInitialized) {
            val allowed = Settings.canDrawOverlays(this)
            permission.setText(if (allowed) R.string.open_panel else R.string.allow_overlay)
            description.setText(if (allowed) R.string.overlay_enabled else R.string.overlay_optional)
            statusDot.background = roundedShape(
                fillColor = getColor(if (allowed) R.color.status_active else R.color.brand_accent),
                radiusDp = 4,
            )
            applyPillCard(
                bannerCard,
                radiusDp = 16,
                fillColorRes = if (allowed) R.color.surface_elevated else R.color.surface_tonal,
                strokeColorRes = R.color.divider_subtle,
            )
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
        AlertDialog.Builder(this, R.style.Theme_Sinan_Dialog)
            .setTitle(R.string.settings)
            .setItems(
                arrayOf(
                    getString(if (Settings.canDrawOverlays(this)) R.string.open_panel else R.string.allow_overlay),
                    getString(R.string.usage_records),
                    getString(if (ball.isEnabled) R.string.ball_disable else R.string.ball_enable),
                    getString(R.string.layout_settings),
                ),
            ) { _, index ->
                when (index) {
                    0 -> if (Settings.canDrawOverlays(this)) openPanel() else requestOverlay()
                    1 -> settingsActions.showUsageRecords()
                    2 -> if (ball.isEnabled) ball.disable() else settingsActions.explainBall()
                    3 -> settingsActions.showLayoutSettings()
                }
            }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        settingsActions.onPermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        settingsActions.dismissDialogs()
        super.onDestroy()
    }

    companion object {
        const val SHOW_SETTINGS = "dev.sinan.launcher.SHOW_SETTINGS"
    }
}
