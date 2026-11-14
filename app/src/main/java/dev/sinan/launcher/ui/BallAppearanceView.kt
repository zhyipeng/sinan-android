package dev.sinan.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.ball.BallStyle
import dev.sinan.launcher.ball.BallStyleDrawable

/** 设置中的实物尺寸预览，样式和透明度使用与悬浮窗口相同的绘制与配置。 */
class BallAppearanceView(context: Context) : LinearLayout(context) {
    private val store = (context.applicationContext as SinanApplication).ballAppearance
    private val preview = ImageView(context)
    private val value = TextView(context)
    private val slider = SeekBar(context)
    private val choices = linkedMapOf<BallStyle, RadioButton>()
    private var previewStyle: BallStyle? = null
    private val observer: () -> Unit = { render() }

    init {
        orientation = VERTICAL
        setPadding(context.dp(4), context.dp(20), context.dp(4), context.dp(8))
        addView(TextView(context).apply {
            setText(R.string.ball_appearance)
            textSize = 18f
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val previewRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(12), 0, context.dp(12))
        }
        val stage = android.widget.FrameLayout(context).apply {
            background = Checkerboard(context.dp(8))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stage.addView(preview, android.widget.FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.CENTER))
        previewRow.addView(stage, LayoutParams(context.dp(72), context.dp(72)))
        previewRow.addView(value.apply {
            textSize = 15f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(context.dp(16), 0, 0, 0)
        }, LayoutParams(0, -2, 1f))
        addView(previewRow)
        slider.min = 20
        slider.max = 100
        slider.progress = 100
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) store.opacity(progress)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) { store.flush() }
        })
        addView(slider, LayoutParams(-1, context.dp(48)))
        BallStyle.entries.chunked(3).forEach { styles ->
            val row = LinearLayout(context)
            styles.forEach { style ->
                val choice = RadioButton(context).apply {
                    id = View.generateViewId()
                    setButtonDrawable(null)
                    setText(style.label)
                    textSize = 13f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.text_primary))
                    setCompoundDrawablesWithIntrinsicBounds(null, BallStyleDrawable(context, style), null, null)
                    compoundDrawablePadding = context.dp(8)
                    setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(8))
                    background = selectionBackground()
                    setOnClickListener { store.select(style) }
                }
                choices[style] = choice
                row.addView(choice, LayoutParams(0, -2, 1f).apply { setMargins(context.dp(2), context.dp(4), context.dp(2), 0) })
            }
            repeat(3 - styles.size) { row.addView(Space(context), LayoutParams(0, 1, 1f)) }
            addView(row)
        }
        render()
    }

    private fun selectionBackground(): Drawable {
        fun border(color: Int) = GradientDrawable().apply {
            cornerRadius = context.dp(10).toFloat()
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(context.dp(2), color)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), border(0xFF2954CE.toInt()))
            addState(intArrayOf(android.R.attr.state_focused), border(0xFF2954CE.toInt()))
            addState(intArrayOf(), border(android.graphics.Color.TRANSPARENT))
        }
    }

    private fun render() {
        val appearance = store.current
        if (previewStyle != appearance.style) {
            previewStyle = appearance.style
            preview.setImageDrawable(BallStyleDrawable(context, appearance.style))
        }
        preview.alpha = appearance.opacity / 100f
        value.text = context.getString(R.string.ball_appearance_value, context.getString(appearance.style.label), appearance.opacity)
        slider.isEnabled = store.isReady
        slider.progress = appearance.opacity
        slider.contentDescription = context.getString(R.string.ball_opacity_value, appearance.opacity)
        choices.forEach { (style, choice) -> choice.isChecked = style == appearance.style; choice.isEnabled = store.isReady }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); store.observe(observer); render() }
    override fun onDetachedFromWindow() { store.flush(); store.removeObserver(observer); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && isAttachedToWindow) store.flush()
    }

    private class Checkerboard(private val size: Int) : Drawable() {
        private val paint = Paint()
        override fun draw(canvas: Canvas) {
            for (y in bounds.top until bounds.bottom step size) for (x in bounds.left until bounds.right step size) {
                paint.color = if (((x - bounds.left) / size + (y - bounds.top) / size) % 2 == 0) 0xFFE4EAF3.toInt() else 0xFFF7F9FD.toInt()
                canvas.drawRect(x.toFloat(), y.toFloat(), minOf(x + size, bounds.right).toFloat(), minOf(y + size, bounds.bottom).toFloat(), paint)
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
    }
}
