package dev.sinan.launcher.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication

/** 追加在既有外观之后；所有滑块共享即时发布、松手保存与无障碍描述。 */
class LayoutSettingsView(context: Context) : LinearLayout(context) {
    private val app = context.applicationContext as SinanApplication
    private val store = app.layoutPreferences
    private val ball = app.ballAppearance
    private val renders = mutableListOf<() -> Unit>()
    private val observer: () -> Unit = { renders.forEach { it() } }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        context.applyPillCard(this, radiusDp = 18, fillColorRes = R.color.surface_elevated, strokeColorRes = R.color.divider_subtle)
        addView(context.heading(16f).apply { setText(R.string.layout_settings) })
        addView(TextView(context).apply {
            setText(R.string.layout_settings_description)
            textSize = 12f
            setTextColor(context.getColor(R.color.text_secondary))
            setPadding(0, context.dp(8), 0, context.dp(4))
        })
        slider(R.string.panel_width, 70, 120, { store.current.panelWidth }, { store.isReady },
            { context.getString(R.string.layout_percent, it) }) { store.update(store.current.copy(panelWidth = it)) }
        slider(R.string.panel_height, 75, 120, { store.current.panelHeight }, { store.isReady },
            { context.getString(R.string.layout_percent, it) }) { store.update(store.current.copy(panelHeight = it)) }
        slider(R.string.panel_opacity, 20, 100, { store.current.panelOpacity }, { store.isReady },
            { context.getString(R.string.layout_percent, it) }) { store.update(store.current.copy(panelOpacity = it)) }
        slider(R.string.ball_size, 32, 80, { ball.current.size }, { ball.isReady },
            { context.getString(R.string.layout_dp, it) }) { ball.size(it) }
        slider(R.string.app_icon_size, 32, 72, { store.current.iconSize }, { store.isReady },
            { context.getString(R.string.layout_dp, it) }) { store.update(store.current.copy(iconSize = it)) }
        slider(R.string.grid_columns, 1, 6, { maxOf(1, store.current.columns) }, { store.isReady },
            { if (it == 1) context.getString(R.string.grid_columns_auto) else context.getString(R.string.grid_columns_value, it) }) {
            store.update(store.current.copy(columns = if (it == 1) 0 else it))
        }
        slider(R.string.keyboard_size, 80, 130, { store.current.keyboardSize }, { store.isReady },
            { context.getString(R.string.layout_percent, it) }) { store.update(store.current.copy(keyboardSize = it)) }
        addView(TextView(context).apply {
            setText(R.string.keyboard_size_description)
            textSize = 12f
            setTextColor(context.getColor(R.color.text_secondary))
        })
        observer()
    }

    private fun slider(title: Int, minValue: Int, maxValue: Int, value: () -> Int, ready: () -> Boolean,
                       format: (Int) -> String, change: (Int) -> Unit) {
        val label = TextView(context).apply {
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(0, context.dp(14), 0, context.dp(2))
        }
        addView(label)
        val slider = SeekBar(context).apply {
            min = minValue
            max = maxValue
            splitTrack = false
            progressDrawable = context.getDrawable(R.drawable.seekbar_track)
            thumb = context.getDrawable(R.drawable.seekbar_thumb)
            setPadding(context.dp(12), 0, context.dp(12), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) change(progress)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) { flush() }
            })
        }
        addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(48)))
        renders.add {
            val description = context.getString(title) + " · " + format(value())
            label.text = description
            slider.contentDescription = description
            slider.progress = value()
            slider.isEnabled = ready()
        }
    }

    private fun flush() { store.flush(); ball.flush() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        store.observe(observer)
        ball.observe(observer)
        observer()
    }
    override fun onDetachedFromWindow() {
        flush()
        store.removeObserver(observer)
        ball.removeObserver(observer)
        super.onDetachedFromWindow()
    }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && isAttachedToWindow) flush()
    }
}
