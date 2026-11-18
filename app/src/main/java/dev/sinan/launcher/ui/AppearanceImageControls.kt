package dev.sinan.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.appearance.AppearanceImage

/** 选择和移除入口在面板设置与普通设置中共用。 */
@SuppressLint("ViewConstructor") // 仅由原生设置页创建，需要共享设置操作。
class AppearanceImageControls(
    context: Context,
    private val kind: AppearanceImage,
    actions: SettingsActions,
) : LinearLayout(context) {
    private val images = (context.applicationContext as SinanApplication).appearanceImages
    private val choose = actionButton { actions.pickAppearanceImage(kind) }
    private val reset = actionButton { actions.clearAppearanceImage(kind) }.apply {
        setText(if (kind == AppearanceImage.PANEL) R.string.panel_image_reset else R.string.ball_image_reset)
    }
    private val observer: () -> Unit = { render() }

    init {
        orientation = VERTICAL
        if (kind == AppearanceImage.PANEL) {
            context.applyPillCard(this, radiusDp = 18, fillColorRes = R.color.surface_elevated, strokeColorRes = R.color.divider_subtle)
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
            addView(context.heading(16f).apply { setText(R.string.panel_image_title) })
        }
        addView(choose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(10)
        })
        addView(reset, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })
        render()
    }

    private fun actionButton(action: () -> Unit) = Button(context).apply {
        context.styleActionButton(
            button = this,
            fillColorRes = R.color.surface_subtle,
            textColorRes = R.color.brand_accent,
            strokeColorRes = R.color.divider_subtle,
            radiusDp = 12,
        )
        minHeight = context.dp(48)
        minimumHeight = minHeight
        attachPressScale()
        setOnClickListener { action() }
    }

    private fun render() {
        val hasImage = images[kind] != null
        val enabled = images.canEdit(kind)
        val label = when {
            images.isReady && !enabled -> R.string.appearance_image_processing
            kind == AppearanceImage.PANEL -> if (hasImage) R.string.panel_image_replace else R.string.panel_image_pick
            else -> if (hasImage) R.string.ball_image_replace else R.string.ball_image_pick
        }
        choose.setText(label)
        choose.isEnabled = enabled
        reset.visibility = if (hasImage) View.VISIBLE else View.GONE
        reset.isEnabled = enabled
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        images.observe(observer)
        render()
    }

    override fun onDetachedFromWindow() {
        images.removeObserver(observer)
        super.onDetachedFromWindow()
    }
}
