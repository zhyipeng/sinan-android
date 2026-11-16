package dev.sinan.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.sinan.launcher.R
import kotlin.math.ceil

/** 应用自带按键，不创建 EditText，也不唤出系统输入法。 */
class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    onDigit: (Char) -> Unit = {},
    onDelete: () -> Unit = {},
    onClear: () -> Unit = {},
    onSettings: () -> Unit = {},
) : LinearLayout(context) {
    private data class KeyHolder(
        val container: LinearLayout,
        val digit: TextView,
        val letters: TextView?,
    )

    private val delete = ImageButton(context)
    private val rows = mutableListOf<LinearLayout>()
    private val digitKeys = mutableListOf<KeyHolder>()
    private var rowHeight = context.dp(54)
    private var verticalMinHeight = context.dp(50)
    private var fillMode = false
    private var stackedKeys = true

    init {
        orientation = VERTICAL
        val labels = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
        val keys: List<View> = (1..9).map { number ->
            createDigitKey(number.toString(), labels[number - 1]) {
                onDigit(('0'.code + number).toChar())
            }
        } + listOf(
            createFunctionKey(
                iconRes = R.drawable.ic_settings,
                description = context.getString(R.string.settings),
                onClick = onSettings,
            ),
            createDigitKey("0", "") {
                onDigit('0')
            },
            createFunctionKey(
                button = delete,
                iconRes = R.drawable.ic_backspace,
                description = context.getString(R.string.input_delete_description),
                onClick = onDelete,
                onLongClick = onClear,
            ),
        )

        computeHeights()
        val keyGap = context.dp(8)
        val chunks = keys.chunked(3)
        chunks.forEachIndexed { rowIndex, group ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            group.forEachIndexed { colIndex, keyView ->
                row.addView(
                    keyView,
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                        if (colIndex < 2) marginEnd = keyGap
                    },
                )
            }
            rows.add(row)
            addView(
                row,
                LayoutParams(LayoutParams.MATCH_PARENT, rowHeight).apply {
                    if (rowIndex < chunks.lastIndex) bottomMargin = keyGap
                },
            )
        }
        setHasInput(false)
    }

    private fun computeHeights() {
        val digitSample = TextView(context).apply {
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        val letterSample = TextView(context).apply {
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        val contentHeight = ceil(
            (digitSample.paint.fontMetrics.let { it.descent - it.ascent } +
                letterSample.paint.fontMetrics.let { it.descent - it.ascent }).toDouble(),
        ).toInt()
        verticalMinHeight = contentHeight + context.dp(10)
        rowHeight = maxOf(context.dp(54), contentHeight + context.dp(18))
    }

    private fun createDigitKey(digit: String, letters: String, onClick: () -> Unit): View {
        val digitView = TextView(context).apply {
            text = digit
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setTextColor(context.getColor(R.color.text_primary))
            gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lettersView = if (letters.isNotEmpty()) {
            TextView(context).apply {
                text = letters
                textSize = 10f
                letterSpacing = 0.08f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                setTextColor(context.getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, context.dp(2), 0, 0)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        } else null

        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isBaselineAligned = false
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.t9_digit_description, digit, letters)
            background = context.rippleSurface(
                fillColor = context.getColor(R.color.key_surface),
                rippleColor = context.getColor(R.color.key_surface_pressed),
                strokeColor = context.getColor(R.color.divider_subtle),
                strokeWidthDp = 1,
                radiusDp = 14,
            )
            setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
            addView(digitView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            if (lettersView != null) addView(lettersView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            attachPressScale(0.95f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
        digitKeys.add(KeyHolder(container, digitView, lettersView))
        return container
    }

    private fun createFunctionKey(
        button: ImageButton = ImageButton(context),
        iconRes: Int,
        description: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ): ImageButton {
        return button.apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.text_primary))
            contentDescription = description
            background = context.rippleSurface(
                fillColor = context.getColor(R.color.key_function_surface),
                rippleColor = context.getColor(R.color.ripple_neutral),
                strokeColor = context.getColor(R.color.divider_subtle),
                strokeWidthDp = 1,
                radiusDp = 14,
            )
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            attachPressScale(0.95f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            if (onLongClick != null) {
                setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                    true
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val perRow = if (fillMode && h > 0) (h - context.dp(6) * 3) / 4 else rowHeight
        val shouldStack = !fillMode || h == 0 || perRow >= verticalMinHeight
        if (stackedKeys != shouldStack) {
            stackedKeys = shouldStack
            applyKeyLayout(shouldStack)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun applyKeyLayout(stacked: Boolean) {
        digitKeys.forEach { holder ->
            holder.container.orientation = if (stacked) VERTICAL else HORIZONTAL
            holder.digit.textSize = if (stacked) 21f else 17f
            holder.letters?.apply {
                textSize = if (stacked) 10f else 9.5f
                setPadding(
                    if (stacked) 0 else context.dp(4),
                    if (stacked) context.dp(2) else context.dp(1),
                    0,
                    0,
                )
            }
        }
    }

    fun setHasInput(hasInput: Boolean) {
        delete.isEnabled = hasInput
        delete.imageAlpha = if (hasInput) 255 else 80
    }

    fun fillHeight(fill: Boolean) {
        fillMode = fill
        val rowGap = context.dp(if (fill) 6 else 8)
        rows.forEachIndexed { index, row ->
            row.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                if (fill) 0 else rowHeight,
                if (fill) 1f else 0f,
            ).apply {
                if (index < rows.lastIndex) bottomMargin = rowGap
            }
        }
    }
}
