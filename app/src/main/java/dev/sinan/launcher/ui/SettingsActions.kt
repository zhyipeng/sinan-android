package dev.sinan.launcher.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.appearance.AppearanceImage
import dev.sinan.launcher.ball.FloatingBallService

/** 两种宿主共享设置操作；面板中的确认窗沿用 Overlay 类型。 */
class SettingsActions(
    private val activity: Activity,
    private val overlay: Boolean = false,
    private val onUsageCleared: () -> Unit,
    private val beforeSystemUi: () -> Unit = {},
    private val onSystemUiFinished: () -> Unit = {},
    private val onChanged: () -> Unit = {},
) {
    private var notificationRequested = false
    private val dialogs = mutableSetOf<AlertDialog>()

    private fun AlertDialog.Builder.showOwned(destructive: Boolean = false) {
        val dialog = create()
        if (overlay) dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isAllCaps = false
            setTextColor(activity.getColor(if (destructive) R.color.danger_accent else R.color.brand_accent))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            isAllCaps = false
            setTextColor(activity.getColor(R.color.text_secondary))
        }
    }

    private fun dialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(activity, R.style.Theme_Sinan_Dialog)

    val hasDialogs get() = dialogs.any { it.isShowing }
    fun dismissTopDialog(): Boolean {
        val dialog = dialogs.lastOrNull { it.isShowing } ?: return false
        dialog.dismiss()
        return true
    }
    fun dismissDialogs() { dialogs.toList().forEach { it.dismiss() } }

    private fun requestOverlay() {
        try {
            beforeSystemUi()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${activity.packageName}")))
        } catch (_: android.content.ActivityNotFoundException) {
            onSystemUiFinished()
            Toast.makeText(activity, R.string.permission_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun explainBall() {
        dialogBuilder()
            .setTitle(R.string.ball_enable)
            .setMessage(R.string.ball_explanation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ball_enable) { _, _ -> enableBall() }
            .showOwned()
    }

    private fun enableBall() {
        if (!Settings.canDrawOverlays(activity)) {
            Toast.makeText(activity, R.string.ball_overlay_needed, Toast.LENGTH_LONG).show()
            requestOverlay()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (notificationRequested && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                FloatingBallService.ensureChannel(activity)
                (activity.application as SinanApplication).ball.enable()
                onChanged()
            } else {
                // 仅在明确开启时请求通知；拒绝后仍可运行，并保留设置中的关闭入口。
                notificationRequested = true
                beforeSystemUi()
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), BALL_NOTIFICATION_REQUEST)
            }
            return
        }
        FloatingBallService.ensureChannel(activity)
        (activity.application as SinanApplication).ball.enable()
        onChanged()
    }

    private fun notificationSettings() {
        dialogBuilder()
            .setMessage(R.string.ball_notification_needed)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ball_notification_settings) { _, _ ->
                try {
                    beforeSystemUi()
                    activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                } catch (_: android.content.ActivityNotFoundException) {
                    onSystemUiFinished()
                    Toast.makeText(activity, R.string.ball_failed, Toast.LENGTH_LONG).show()
                }
            }.showOwned(destructive = true)
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != BALL_NOTIFICATION_REQUEST) return
        onSystemUiFinished()
        if (!activity.isFinishing) {
            FloatingBallService.ensureChannel(activity)
            (activity.application as SinanApplication).ball.enable()
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(activity, R.string.ball_notification_optional, Toast.LENGTH_LONG).show()
        }
        onChanged()
    }

    fun showUsageRecords() {
        dialogBuilder()
            .setTitle(R.string.usage_records)
            .setMessage(R.string.usage_records_description)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.usage_clear) { _, _ ->
                (activity.application as SinanApplication).apps.clearUsage { result ->
                    if (activity.isFinishing || activity.isDestroyed) return@clearUsage
                    result.fold(onSuccess = {
                        Toast.makeText(activity, R.string.usage_cleared, Toast.LENGTH_SHORT).show()
                        onUsageCleared()
                    }, onFailure = { Toast.makeText(activity, R.string.usage_clear_failed, Toast.LENGTH_LONG).show() })
                }
            }.showOwned(destructive = true)
    }

    fun showLayoutSettings() {
        val content = android.widget.ScrollView(activity).apply {
            addView(LayoutSettingsView(activity))
        }
        dialogBuilder().setTitle(R.string.layout_settings).setView(content)
            .setPositiveButton(android.R.string.ok, null).showOwned()
    }

    fun showBallBackgroundSettings() {
        val exempt = activity.getSystemService(android.os.PowerManager::class.java)
            .isIgnoringBatteryOptimizations(activity.packageName)
        dialogBuilder().setTitle(R.string.ball_background)
            .setItems(arrayOf(
                activity.getString(if (exempt) R.string.ball_battery_exempt else R.string.ball_battery_settings),
                activity.getString(R.string.ball_vendor_settings),
                activity.getString(R.string.ball_notification_settings),
            )) { _, index ->
                when (index) {
                    0 -> openBackgroundSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    1 -> dialogBuilder().setMessage(R.string.ball_vendor_instructions)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.ball_vendor_settings) { _, _ ->
                            openBackgroundSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${activity.packageName}")))
                        }.showOwned()
                    2 -> notificationSettings()
                }
            }.showOwned()
    }

    private fun openBackgroundSettings(intent: Intent) {
        try {
            beforeSystemUi()
            activity.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            onSystemUiFinished()
            Toast.makeText(activity, R.string.permission_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun showCustomAppearance() {
        val content = ScrollView(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = activity.dp(16)
                setPadding(padding, padding, padding, padding)
                addView(BallAppearanceView(activity, this@SettingsActions))
                addView(AppearanceImageControls(activity, AppearanceImage.PANEL, this@SettingsActions),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = padding
                    })
            })
        }
        dialogBuilder().setTitle(R.string.appearance_images).setView(content)
            .setPositiveButton(android.R.string.ok, null).showOwned()
    }

    @Suppress("DEPRECATION")
    fun pickAppearanceImage(kind: AppearanceImage) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            beforeSystemUi()
            activity.startActivityForResult(intent, kind.requestCode)
        } catch (_: ActivityNotFoundException) {
            onSystemUiFinished()
            Toast.makeText(activity, R.string.appearance_picker_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun onAppearanceImageResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val kind = AppearanceImage.entries.firstOrNull { it.requestCode == requestCode } ?: return
        onSystemUiFinished()
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data
        if (uri?.scheme != "content") {
            reportImageFailure(R.string.appearance_import_failed)
            return
        }
        (activity.application as SinanApplication).appearanceImages.import(kind, uri) { success ->
            if (!success) reportImageFailure(R.string.appearance_import_failed)
        }
    }

    fun clearAppearanceImage(kind: AppearanceImage, onCleared: () -> Unit = {}) {
        (activity.application as SinanApplication).appearanceImages.clear(kind) { success ->
            if (success) onCleared() else reportImageFailure(R.string.appearance_reset_failed)
        }
    }

    private fun reportImageFailure(message: Int) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object { private const val BALL_NOTIFICATION_REQUEST = 210 }
}
