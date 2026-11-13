package dev.sinan.launcher.ui

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import dev.sinan.launcher.R

/** 应用自带按键，不创建 EditText，也不唤出系统输入法。 */
class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    onDigit: (Char) -> Unit = {},
    onDelete: () -> Unit = {},
    onClear: () -> Unit = {},
) : LinearLayout(context) {
    private val clear = Button(context)
    private val delete = Button(context)
    private var rowHeight = context.dp(52)

    init {
        orientation = VERTICAL
        val labels = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
        val buttons = (1..9).map { number ->
            val letters = labels[number - 1]
            Button(context).apply {
                text = if (letters.isEmpty()) number.toString() else "$number\n$letters"
                contentDescription = context.getString(R.string.t9_digit_description, number.toString(), letters)
                setOnClickListener { onDigit(('0'.code + number).toChar()) }
            }
        } + listOf(
            clear.apply { setText(R.string.input_clear); setOnClickListener { onClear() } },
            Button(context).apply {
                text = "0"
                contentDescription = context.getString(R.string.t9_digit_description, "0", "")
                setOnClickListener { onDigit('0') }
            },
            delete.apply {
                setText(R.string.input_delete)
                setOnClickListener { onDelete() }
                setOnLongClickListener { onClear(); true }
            },
        )
        buttons.chunked(3).forEach { group ->
            val row = LinearLayout(context)
            group.forEach { button ->
                button.isAllCaps = false
                button.textSize = 15f
                button.gravity = Gravity.CENTER
                button.minWidth = 0
                button.minimumWidth = 0
                button.setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                rowHeight = maxOf(rowHeight, button.lineHeight * 2 + context.dp(12))
                row.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        }
        setHasInput(false)
    }

    fun setHasInput(hasInput: Boolean) {
        clear.isEnabled = hasInput
        delete.isEnabled = hasInput
    }

    fun fillHeight(fill: Boolean) {
        for (i in 0 until childCount) {
            getChildAt(i).layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, if (fill) 0 else rowHeight, if (fill) 1f else 0f)
        }
    }
}
