package dev.sinan.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication

/** 面板内第二页；保留原网格实例，以保持查询、滚动和当前会话排序。 */
// 仅代码构建，宿主和操作回调为必需参数，不支持 XML 膨胀。
@SuppressLint("ViewConstructor")
class SettingsPage(activity: Activity, onBack: () -> Unit, actions: SettingsActions) : ScrollView(activity) {
    private val controller = (activity.application as SinanApplication).ball
    private val ballButton = Button(activity)
    private val observer: () -> Unit = { refresh() }

    init {
        isFillViewport = true
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(activity.getColor(R.color.text_primary))
            contentDescription = activity.getString(R.string.back_to_search)
            val background = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
            setBackgroundResource(background.resourceId)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)))
        header.addView(activity.heading().apply { setText(R.string.settings) })
        column.addView(header)
        column.addView(Button(activity).apply {
            setText(R.string.usage_records)
            setOnClickListener { actions.showUsageRecords() }
        }, LinearLayout.LayoutParams(-1, -2))
        ballButton.setOnClickListener {
            if (controller.isEnabled) controller.disable() else actions.explainBall()
        }
        column.addView(ballButton, LinearLayout.LayoutParams(-1, -2))
        column.addView(BallAppearanceView(activity))
        addView(column)
        refresh()
    }

    fun refresh() { ballButton.setText(if (controller.isEnabled) R.string.ball_disable else R.string.ball_enable) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); controller.observe(observer); refresh() }
    override fun onDetachedFromWindow() { controller.removeObserver(observer); super.onDetachedFromWindow() }
}
