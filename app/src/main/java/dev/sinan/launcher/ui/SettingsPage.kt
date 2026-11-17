package dev.sinan.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication

/** 面板内第二页；保留原网格实例，以保持查询、滚动和当前会话排序。 */
// 仅代码构建，宿主和操作回调为必需参数，不支持 XML 膨胀。
@SuppressLint("ViewConstructor")
class SettingsPage(activity: Activity, onBack: () -> Unit, actions: SettingsActions) : ScrollView(activity) {
    private val controller = (activity.application as SinanApplication).ball
    private val ballStatusDot = View(activity)
    private val ballStatusText = TextView(activity)
    private val ballButton = Button(activity)
    private val observer: () -> Unit = { refresh() }

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(2), 0, activity.dp(8))
        }

        // 顶部栏
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(14))
        }
        header.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = ColorStateList.valueOf(activity.getColor(R.color.text_primary))
            contentDescription = activity.getString(R.string.back_to_search)
            background = activity.rippleSurface(
                fillColor = activity.getColor(R.color.surface_elevated),
                rippleColor = activity.getColor(R.color.ripple_neutral),
                strokeColor = activity.getColor(R.color.divider_subtle),
                strokeWidthDp = 1,
                radiusDp = 12,
            )
            setPadding(0, 0, 0, 0)
            attachPressScale(0.94f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onBack()
            }
        }, LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)))
        header.addView(activity.heading(21f).apply {
            setText(R.string.settings)
            setPadding(activity.dp(12), 0, 0, 0)
        })
        column.addView(header)

        // 核心设置分组卡片
        val groupCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            activity.applyPillCard(
                view = this,
                radiusDp = 18,
                fillColorRes = R.color.surface_elevated,
                strokeColorRes = R.color.divider_subtle,
            )
        }

        // 第一项：悬浮球状态与切换
        val ballRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
        }
        ballRow.addView(createBadge(activity, R.drawable.ic_floating_ball, R.color.surface_tonal),
            LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)).apply {
                marginEnd = activity.dp(12)
            })
        val ballTextColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val ballTitleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ballTitleRow.addView(TextView(activity).apply {
            setText(R.string.ball_title)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(activity.getColor(R.color.text_primary))
        })
        ballTitleRow.addView(ballStatusDot, LinearLayout.LayoutParams(activity.dp(7), activity.dp(7)).apply {
            marginStart = activity.dp(7)
        })
        ballTextColumn.addView(ballTitleRow)
        ballStatusText.apply {
            textSize = 12f
            setTextColor(activity.getColor(R.color.text_secondary))
            setPadding(0, activity.dp(3), activity.dp(8), 0)
        }
        ballTextColumn.addView(ballStatusText)
        ballRow.addView(ballTextColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        ballButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (controller.isEnabled) controller.disable() else actions.explainBall()
        }
        ballRow.addView(ballButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        groupCard.addView(ballRow)

        // 分割线
        groupCard.addView(View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.divider_subtle))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, activity.dp(1))).apply {
            marginStart = activity.dp(14)
            marginEnd = activity.dp(14)
        })

        // 第二项：使用记录
        val usageRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            background = activity.rippleSurface(
                fillColor = Color.TRANSPARENT,
                rippleColor = activity.getColor(R.color.ripple_neutral),
                radiusDp = 0,
            )
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                actions.showUsageRecords()
            }
        }
        usageRow.addView(createBadge(activity, R.drawable.ic_history, R.color.surface_subtle),
            LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)).apply {
                marginEnd = activity.dp(12)
            })
        val usageTextColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        usageTextColumn.addView(TextView(activity).apply {
            setText(R.string.usage_records)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(activity.getColor(R.color.text_primary))
        })
        usageTextColumn.addView(TextView(activity).apply {
            setText(R.string.usage_records_subtitle)
            textSize = 12f
            setTextColor(activity.getColor(R.color.text_secondary))
            setPadding(0, activity.dp(3), activity.dp(8), 0)
        })
        usageRow.addView(usageTextColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        usageRow.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)))
        groupCard.addView(usageRow)

        column.addView(groupCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(14)
        })
        column.addView(BallAppearanceView(activity))
        column.addView(LayoutSettingsView(activity), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(14)
        })
        addView(column)
        refresh()
    }

    private fun createBadge(activity: Activity, iconRes: Int, bgColorRes: Int): FrameLayout {
        return FrameLayout(activity).apply {
            background = activity.roundedShape(
                fillColor = activity.getColor(bgColorRes),
                radiusDp = 12,
            )
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(ImageView(activity).apply {
                setImageResource(iconRes)
            }, FrameLayout.LayoutParams(activity.dp(20), activity.dp(20), Gravity.CENTER))
        }
    }

    fun refresh() {
        val enabled = controller.isEnabled
        ballStatusDot.background = context.roundedShape(
            fillColor = context.getColor(if (enabled) R.color.status_active else R.color.text_tertiary),
            radiusDp = 4,
        )
        ballStatusText.setText(if (enabled) R.string.ball_status_on else R.string.ball_status_off)
        ballButton.setText(if (enabled) R.string.ball_disable else R.string.ball_enable)
        context.styleActionButton(
            button = ballButton,
            fillColorRes = if (enabled) R.color.danger_surface else R.color.surface_tonal,
            textColorRes = if (enabled) R.color.danger_accent else R.color.brand_accent,
            radiusDp = 12,
            textSizeSp = 13f,
            horizontalPaddingDp = 14,
            verticalPaddingDp = 8,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.observe(observer)
        refresh()
    }

    override fun onDetachedFromWindow() {
        controller.removeObserver(observer)
        super.onDetachedFromWindow()
    }
}
