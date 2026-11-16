package dev.sinan.launcher.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
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
                notificationSettings()
            } else {
                // 同一设置会话内拒绝后可转到系统通知设置，不保存开启状态。
                notificationRequested = true
                beforeSystemUi()
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), BALL_NOTIFICATION_REQUEST)
            }
            return
        }
        FloatingBallService.ensureChannel(activity)
        if (!FloatingBallService.notificationsAvailable(activity)) { notificationSettings(); return }
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
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && !activity.isFinishing) enableBall()
        else Toast.makeText(activity, R.string.ball_notification_needed, Toast.LENGTH_LONG).show()
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

    companion object { private const val BALL_NOTIFICATION_REQUEST = 210 }
}
