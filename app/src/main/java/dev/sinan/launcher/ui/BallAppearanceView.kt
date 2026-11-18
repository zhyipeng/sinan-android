package dev.sinan.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.appearance.AppearanceImage
import dev.sinan.launcher.appearance.AppearanceImageDrawable
import dev.sinan.launcher.ball.BallStyle
import dev.sinan.launcher.ball.BallStyleDrawable

/** 设置中的实物尺寸预览，样式和透明度使用与悬浮窗口相同的绘制与配置。 */
@SuppressLint("ViewConstructor") // 仅由原生设置页创建，需要共享设置操作。
class BallAppearanceView(context: Context, actions: SettingsActions) : LinearLayout(context) {
    private val app = context.applicationContext as SinanApplication
    private val store = app.ballAppearance
    private val images = app.appearanceImages
    private val preview = ImageView(context)
    private val value = TextView(context)
    private val slider = SeekBar(context)
    private val choices = linkedMapOf<BallStyle, RadioButton>()
    private var previewStyle: BallStyle? = null
    private var previewImage: Bitmap? = null
    private val observer: () -> Unit = { render() }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        context.applyPillCard(
            view = this,
            radiusDp = 18,
            fillColorRes = R.color.surface_elevated,
            strokeColorRes = R.color.divider_subtle,
        )

        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(context).apply {
            setText(R.string.ball_appearance)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_primary))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        value.apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(context.getColor(R.color.brand_accent))
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
            background = context.roundedShape(
                fillColor = context.getColor(R.color.surface_tonal),
                radiusDp = 12,
            )
        }
        headerRow.addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(12)
        })

        val stage = FrameLayout(context).apply {
            background = Checkerboard(
                size = context.dp(8),
                lightColor = context.getColor(R.color.checker_light),
                darkColor = context.getColor(R.color.checker_dark),
            )
            foreground = context.roundedShape(
                fillColor = Color.TRANSPARENT,
                strokeColor = context.getColor(R.color.divider_subtle),
                strokeWidthDp = 1,
                radiusDp = 14,
            )
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, context.dpF(14))
                }
            }
            clipToOutline = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stage.addView(preview, FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.CENTER))
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(84)).apply {
            bottomMargin = context.dp(12)
        })

        slider.apply {
            min = 20
            max = 100
            progress = 100
            splitTrack = false
            progressDrawable = context.getDrawable(R.drawable.seekbar_track)
            thumb = context.getDrawable(R.drawable.seekbar_thumb)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) store.opacity(progress)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) { store.flush() }
            })
        }
        addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(40)))

        val scaleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(context.dp(8), 0, context.dp(8), context.dp(12))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        listOf(
            R.string.ball_opacity_min to Gravity.START,
            R.string.ball_opacity_mid to Gravity.CENTER_HORIZONTAL,
            R.string.ball_opacity_max to Gravity.END,
        ).forEach { (resId, align) ->
            scaleRow.addView(TextView(context).apply {
                setText(resId)
                textSize = 11f
                fontFeatureSettings = "tnum"
                setTextColor(context.getColor(R.color.text_tertiary))
                gravity = align
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(scaleRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val gap = context.dp(8)
        val rows = BallStyle.entries.chunked(3)
        rows.forEachIndexed { rowIndex, styles ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            styles.forEachIndexed { colIndex, style ->
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
                    setPadding(context.dp(4), context.dp(12), context.dp(4), context.dp(12))
                    background = selectionBackground()
                    attachPressScale(0.96f)
                    setOnClickListener {
                        if (images[AppearanceImage.BALL] == null) store.select(style)
                        else actions.clearAppearanceImage(AppearanceImage.BALL) { store.select(style) }
                    }
                }
                choices[style] = choice
                row.addView(choice, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (colIndex < 2) marginEnd = gap
                })
            }
            repeat(3 - styles.size) { colOffset ->
                val index = styles.size + colOffset
                row.addView(Space(context), LayoutParams(0, 1, 1f).apply {
                    if (index < 2) marginEnd = gap
                })
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex < rows.lastIndex) bottomMargin = gap
            })
        }
        addView(AppearanceImageControls(context, AppearanceImage.BALL, actions))
        render()
    }

    private fun selectionBackground(): Drawable {
        val checked = context.roundedShape(
            fillColor = context.getColor(R.color.surface_tonal),
            strokeColor = context.getColor(R.color.brand_accent),
            strokeWidthDp = 2,
            radiusDp = 14,
        )
        val idle = context.roundedShape(
            fillColor = context.getColor(R.color.surface_subtle),
            strokeColor = context.getColor(R.color.divider_subtle),
            strokeWidthDp = 1,
            radiusDp = 14,
        )
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checked)
            addState(intArrayOf(android.R.attr.state_focused), checked)
            addState(intArrayOf(), idle)
        }
    }

    private fun render() {
        val appearance = store.current
        val image = images[AppearanceImage.BALL]
        if (previewStyle != appearance.style || previewImage !== image) {
            previewStyle = appearance.style
            previewImage = image
            preview.setImageDrawable(
                if (image == null) BallStyleDrawable(context, appearance.style)
                else AppearanceImageDrawable(context, image, AppearanceImage.BALL),
            )
        }
        preview.alpha = appearance.opacity / 100f
        preview.layoutParams = (preview.layoutParams as FrameLayout.LayoutParams).apply {
            width = context.dp(appearance.size)
            height = context.dp(appearance.size)
        }
        value.text = context.getString(
            R.string.ball_appearance_value,
            context.getString(if (image == null) appearance.style.label else R.string.ball_style_custom),
            appearance.opacity,
        )
        slider.isEnabled = store.isReady
        slider.progress = appearance.opacity
        slider.contentDescription = context.getString(R.string.ball_opacity_value, appearance.opacity)
        choices.forEach { (style, choice) ->
            val selected = image == null && style == appearance.style
            choice.isChecked = selected
            choice.isEnabled = store.isReady && images.canEdit(AppearanceImage.BALL)
            choice.setTextColor(context.getColor(if (selected) R.color.brand_accent else R.color.text_primary))
            choice.typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        store.observe(observer)
        images.observe(observer)
        render()
    }

    override fun onDetachedFromWindow() {
        store.flush()
        store.removeObserver(observer)
        images.removeObserver(observer)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && isAttachedToWindow) store.flush()
    }

    private class Checkerboard(
        private val size: Int,
        private val lightColor: Int,
        private val darkColor: Int,
    ) : Drawable() {
        private val paint = Paint()
        override fun draw(canvas: Canvas) {
            for (y in bounds.top until bounds.bottom step size) {
                for (x in bounds.left until bounds.right step size) {
                    paint.color = if (((x - bounds.left) / size + (y - bounds.top) / size) % 2 == 0) lightColor else darkColor
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        minOf(x + size, bounds.right).toFloat(),
                        minOf(y + size, bounds.bottom).toFloat(),
                        paint,
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Drawable API")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
