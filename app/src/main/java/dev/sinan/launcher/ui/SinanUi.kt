package dev.sinan.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import dev.sinan.launcher.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.dpF(value: Int): Float = value * resources.displayMetrics.density

fun Context.heading(sizeSp: Float = 22f): TextView = TextView(this).apply {
    textSize = sizeSp
    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    setTextColor(getColor(R.color.text_primary))
    letterSpacing = -0.01f
    if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
}

fun Context.roundedShape(
    fillColor: Int,
    strokeColor: Int? = null,
    strokeWidthDp: Int = 1,
    radiusDp: Int = 16,
): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dpF(radiusDp)
    setColor(fillColor)
    if (strokeColor != null && strokeWidthDp > 0) {
        setStroke(maxOf(1, dp(strokeWidthDp)), strokeColor)
    }
}

fun Context.rippleSurface(
    fillColor: Int,
    rippleColor: Int = getColor(R.color.ripple_neutral),
    strokeColor: Int? = null,
    strokeWidthDp: Int = 1,
    radiusDp: Int = 14,
): Drawable {
    val content = roundedShape(fillColor, strokeColor, strokeWidthDp, radiusDp)
    val mask = roundedShape(Color.WHITE, radiusDp = radiusDp)
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
}

fun Context.applyPillCard(view: View, radiusDp: Int = 16, fillColorRes: Int = R.color.surface_elevated, strokeColorRes: Int? = R.color.divider_subtle) {
    view.background = roundedShape(
        fillColor = getColor(fillColorRes),
        strokeColor = strokeColorRes?.let { getColor(it) },
        strokeWidthDp = 1,
        radiusDp = radiusDp,
    )
    view.clipToOutline = true
    view.outlineProvider = ViewOutlineProvider.BACKGROUND
}

fun Context.styleActionButton(
    button: Button,
    fillColorRes: Int = R.color.brand_accent,
    textColorRes: Int = R.color.brand_accent_on,
    strokeColorRes: Int? = null,
    rippleColorRes: Int = R.color.ripple_neutral,
    radiusDp: Int = 12,
    textSizeSp: Float = 14f,
    horizontalPaddingDp: Int = 16,
    verticalPaddingDp: Int = 10,
) {
    button.apply {
        stateListAnimator = null
        elevation = 0f
        isAllCaps = false
        minHeight = dp(40)
        minimumHeight = dp(40)
        minWidth = 0
        minimumWidth = 0
        textSize = textSizeSp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(getColor(textColorRes))
        setPadding(dp(horizontalPaddingDp), dp(verticalPaddingDp), dp(horizontalPaddingDp), dp(verticalPaddingDp))
        background = rippleSurface(
            fillColor = getColor(fillColorRes),
            rippleColor = getColor(rippleColorRes),
            strokeColor = strokeColorRes?.let { getColor(it) },
            strokeWidthDp = 1,
            radiusDp = radiusDp,
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
fun View.attachPressScale(pressedScale: Float = 0.96f) {
    setOnTouchListener { view, event ->
        if (!view.isEnabled) return@setOnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().cancel()
                view.animate().scaleX(pressedScale).scaleY(pressedScale).setDuration(70).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().cancel()
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
        }
        false
    }
}

fun extractSystemInsets(insets: WindowInsets): Rect {
    @Suppress("DEPRECATION")
    return if (Build.VERSION.SDK_INT >= 30) {
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        Rect(bars.left, bars.top, bars.right, bars.bottom)
    } else {
        Rect(
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetTop,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom,
        )
    }
}
