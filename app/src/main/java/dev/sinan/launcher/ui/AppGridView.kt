package dev.sinan.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.apps.AppSnapshot
import dev.sinan.launcher.apps.LaunchableApp
import dev.sinan.search.T9SearchEngine
import java.util.concurrent.Future

class AppGridView @JvmOverloads constructor(
    context: Context,
    onSettings: () -> Unit = {},
    private val onLaunched: () -> Unit = {},
) : LinearLayout(context) {
    private val repository = (context.applicationContext as SinanApplication).apps
    private val emptyState = LinearLayout(context)
    private val status = TextView(context)
    private val retry = Button(context)
    private val searchIcon = ImageView(context)
    private val queryDisplay = TextView(context)
    private val clearButton = ImageButton(context)
    private val queryBar = LinearLayout(context)
    private val resultsColumn = LinearLayout(context).apply { orientation = VERTICAL }
    private val keyboardColumn = LinearLayout(context).apply { orientation = VERTICAL }
    private val grid = object : GridView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressedVersion = searchRevision
                    pressedApp = apps.getOrNull(pointToPosition(event.x.toInt(), event.y.toInt()))
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressedVersion = null
                    pressedApp = null
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private var apps = emptyList<LaunchableApp>()
    private var snapshot: AppSnapshot? = null
    private var load: Future<*>? = null
    private var searchTask: Future<*>? = null
    private var generation = 0
    private var searchRevision = 0
    private var query = ""
    private var compact = false
    private var layoutWidth = 0
    private val verticalResults = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
    private val horizontalResults = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
    private val verticalKeys = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    private val horizontalKeys = LayoutParams(0, LayoutParams.MATCH_PARENT).apply { marginStart = context.dp(12) }
    private var pressedVersion: Int? = null
    private var pressedApp: LaunchableApp? = null
    private var launching = false
    private val adapter = AppAdapter()
    private val keyboard = T9KeyboardView(
        context,
        onDigit = { if (query.length < T9SearchEngine.MAX_QUERY_LENGTH) changeQuery(query + it) },
        onDelete = { changeQuery(query.dropLast(1)) },
        onClear = { changeQuery("") },
        onSettings = onSettings,
    )

    private fun dp(value: Int) = context.dp(value)

    init {
        orientation = VERTICAL

        // 居中的加载/空结果/重试状态视图
        emptyState.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(FrameLayout(context).apply {
                background = context.roundedShape(
                    fillColor = context.getColor(R.color.surface_subtle),
                    radiusDp = 24,
                )
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_empty_search)
                    imageTintList = ColorStateList.valueOf(context.getColor(R.color.text_secondary))
                }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            }, LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(12) })
        }
        status.apply {
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        emptyState.addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        retry.apply {
            setText(R.string.retry)
            context.styleActionButton(
                button = this,
                fillColorRes = R.color.surface_tonal,
                textColorRes = R.color.brand_accent,
                radiusDp = 20,
                horizontalPaddingDp = 20,
                verticalPaddingDp = 8,
            )
            setOnClickListener { refresh() }
            visibility = GONE
        }
        emptyState.addView(retry, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        // 应用网格
        grid.apply {
            numColumns = 4
            addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val count = ((right - left) / dp(84)).coerceIn(2, 6)
                if (numColumns != count) numColumns = count
            }
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(4)
            verticalSpacing = dp(4)
            setPadding(0, dp(4), 0, dp(10))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(18))
            adapter = this@AppGridView.adapter
            setOnItemClickListener { _, _, position, _ ->
                val downVersion = pressedVersion
                val target = pressedApp
                pressedVersion = null
                pressedApp = null
                if (downVersion != null && downVersion != searchRevision) return@setOnItemClickListener
                if (!launching && isEnabled) {
                    val app = target ?: apps.getOrNull(position) ?: return@setOnItemClickListener
                    launching = true
                    if (repository.launch(app)) onLaunched() else {
                        launching = false
                        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_LONG).show()
                        refresh()
                    }
                }
            }
        }

        val resultsContainer = FrameLayout(context)
        resultsContainer.addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        resultsContainer.addView(emptyState, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        resultsColumn.addView(resultsContainer, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(resultsColumn, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 数字胶囊显示栏
        queryBar.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            context.applyPillCard(
                view = this,
                radiusDp = 14,
                fillColorRes = R.color.surface_elevated,
                strokeColorRes = R.color.divider_subtle,
            )
        }
        searchIcon.apply {
            setImageResource(R.drawable.ic_search)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        queryDisplay.apply {
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            letterSpacing = 0.08f
            includeFontPadding = false
            setTextColor(context.getColor(R.color.text_primary))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            contentDescription = context.getString(R.string.input_query, "")
        }
        clearButton.apply {
            setImageResource(R.drawable.ic_clear)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.text_secondary))
            background = context.rippleSurface(
                fillColor = Color.TRANSPARENT,
                rippleColor = context.getColor(R.color.ripple_neutral),
                radiusDp = 18,
            )
            contentDescription = context.getString(R.string.input_clear_description)
            visibility = INVISIBLE
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                changeQuery("")
            }
        }
        queryBar.addView(searchIcon, LayoutParams(dp(36), dp(36)))
        queryBar.addView(queryDisplay, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        queryBar.addView(clearButton, LayoutParams(dp(36), dp(36)))
        updateQueryBarStyle(hasInput = false)

        keyboardColumn.addView(queryBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            bottomMargin = dp(10)
        })
        keyboardColumn.addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(keyboardColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val horizontal = h > 0 && w > h
        // 在测量前切换布局，不能在 onSizeChanged 中让本次布局沿用旧方向的测量结果。
        if (compact != horizontal || layoutWidth != w) {
            compact = horizontal
            layoutWidth = w
            orientation = if (horizontal) HORIZONTAL else VERTICAL
            horizontalKeys.width = minOf((w * 0.44f).toInt(), dp(256))
            resultsColumn.layoutParams = if (horizontal) horizontalResults else verticalResults
            keyboardColumn.layoutParams = if (horizontal) horizontalKeys else verticalKeys
            queryBar.minimumHeight = dp(if (horizontal) 38 else 44)
            queryBar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (horizontal) 0 else 4)
                bottomMargin = dp(if (horizontal) 6 else 10)
            }
            keyboard.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                if (horizontal) 0 else LayoutParams.WRAP_CONTENT,
                if (horizontal) 1f else 0f,
            )
            keyboard.fillHeight(horizontal)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        generation++
        searchRevision++
        load?.cancel(true)
        searchTask?.cancel(true)
        super.onDetachedFromWindow()
    }

    fun refreshUsage() = refresh()

    private fun refresh() {
        val version = ++generation
        load?.cancel(true)
        searchTask?.cancel(true)
        grid.isEnabled = false
        retry.visibility = GONE
        emptyState.visibility = VISIBLE
        status.setText(R.string.loading_apps)
        load = repository.load { result ->
            if (generation != version || !isAttachedToWindow) return@load
            result.fold(onSuccess = {
                snapshot = it
                search()
            }, onFailure = {
                emptyState.visibility = VISIBLE
                status.setText(R.string.apps_failed)
                retry.visibility = VISIBLE
            })
        }
    }

    private fun changeQuery(value: String) {
        if (query == value) return
        query = value
        queryDisplay.text = value
        queryDisplay.contentDescription = context.getString(R.string.input_query, value)
        val hasInput = value.isNotEmpty()
        updateQueryBarStyle(hasInput)
        keyboard.setHasInput(hasInput)
        search()
    }

    private fun updateQueryBarStyle(hasInput: Boolean) {
        searchIcon.imageTintList = ColorStateList.valueOf(
            context.getColor(if (hasInput) R.color.brand_accent else R.color.text_tertiary),
        )
        searchIcon.alpha = if (hasInput) 1f else 0.7f
        clearButton.visibility = if (hasInput) VISIBLE else INVISIBLE
    }

    private fun search() {
        val revision = ++searchRevision
        searchTask?.cancel(true)
        grid.isEnabled = false
        val data = snapshot ?: return
        val currentGeneration = generation
        searchTask = repository.search(data, query) results@{ found ->
            if (revision != searchRevision || currentGeneration != generation || !isAttachedToWindow || launching) return@results
            apps = found
            adapter.notifyDataSetChanged()
            grid.setSelection(0)
            grid.isEnabled = true
            status.setText(if (query.isEmpty()) R.string.no_apps else R.string.no_matches)
            emptyState.visibility = if (found.isEmpty()) VISIBLE else GONE
            retry.visibility = if (query.isEmpty() && found.isEmpty()) VISIBLE else GONE
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = convertView as? LinearLayout ?: LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                minimumHeight = dp(96)
                background = context.rippleSurface(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = context.getColor(R.color.ripple_neutral),
                    radiusDp = 16,
                )
                addView(ImageView(context).apply {
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LayoutParams(dp(48), dp(48)))
                addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(context.getColor(R.color.text_primary))
                    setLineSpacing(context.dpF(2), 1f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(7), 0, 0)
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            val app = getItem(position)
            val icon = cell.getChildAt(0) as ImageView
            (cell.getChildAt(1) as TextView).text = app.entry.label
            cell.contentDescription = app.entry.label
            icon.tag = app.entry.id
            icon.setImageResource(android.R.drawable.sym_def_app_icon)
            val bindingGeneration = generation
            repository.loadIcon(app) { drawable ->
                // 缓存命中会同步回调，getView 此时可能尚未挂载到窗口。
                if (icon.tag == app.entry.id && generation == bindingGeneration && drawable != null) {
                    icon.setImageDrawable(drawable)
                }
            }
            return cell
        }
    }
}
