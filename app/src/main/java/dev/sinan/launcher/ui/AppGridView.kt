package dev.sinan.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.sinan.launcher.R
import dev.sinan.launcher.SinanApplication
import dev.sinan.launcher.apps.LaunchableApp
import dev.sinan.launcher.apps.AppSnapshot
import dev.sinan.search.T9SearchEngine
import java.util.concurrent.Future

class AppGridView @JvmOverloads constructor(context: Context, private val onLaunched: () -> Unit = {}) : LinearLayout(context) {
    private val repository = (context.applicationContext as SinanApplication).apps
    private val status = TextView(context)
    private val retry = Button(context)
    private val queryDisplay = TextView(context)
    private val resultsColumn = LinearLayout(context).apply { orientation = VERTICAL }
    private val grid = object : GridView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressedVersion = searchRevision
                    pressedApp = apps.getOrNull(pointToPosition(event.x.toInt(), event.y.toInt()))
                }
                MotionEvent.ACTION_CANCEL -> { pressedVersion = null; pressedApp = null }
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
    private val horizontalKeys = LayoutParams(0, LayoutParams.MATCH_PARENT).apply { marginStart = context.dp(8) }
    private var pressedVersion: Int? = null
    private var pressedApp: LaunchableApp? = null
    private var launching = false
    private val adapter = AppAdapter()
    private val keyboard = T9KeyboardView(context,
        onDigit = { if (query.length < T9SearchEngine.MAX_QUERY_LENGTH) changeQuery(query + it) },
        onDelete = { changeQuery(query.dropLast(1)) },
        onClear = { changeQuery("") },
    )
    private fun dp(value: Int) = context.dp(value)

    init {
        orientation = VERTICAL
        queryDisplay.textSize = 14f
        queryDisplay.setTextColor(context.getColor(R.color.text_primary))
        queryDisplay.minHeight = dp(44)
        queryDisplay.maxLines = 1
        queryDisplay.ellipsize = TextUtils.TruncateAt.START
        queryDisplay.setPadding(dp(8), dp(4), dp(8), dp(4))
        queryDisplay.setText(R.string.input_hint)
        resultsColumn.addView(queryDisplay)
        status.setTextColor(context.getColor(R.color.text_secondary))
        status.textSize = 13f
        status.setPadding(dp(8), dp(6), dp(8), dp(8))
        resultsColumn.addView(status)
        retry.setText(R.string.retry)
        retry.setOnClickListener { refresh() }
        retry.visibility = GONE
        resultsColumn.addView(retry)
        grid.numColumns = 4
        grid.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val count = ((right - left) / dp(88)).coerceIn(2, 6)
            if (grid.numColumns != count) grid.numColumns = count
        }
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = dp(4)
        grid.verticalSpacing = dp(8)
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val downVersion = pressedVersion
            val target = pressedApp
            pressedVersion = null
            pressedApp = null
            if (downVersion != null && downVersion != searchRevision) return@setOnItemClickListener
            if (!launching && grid.isEnabled) {
                val app = target ?: apps.getOrNull(position) ?: return@setOnItemClickListener
                launching = true
                if (repository.launch(app)) onLaunched() else {
                    launching = false
                    Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }
        resultsColumn.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(resultsColumn, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
            horizontalKeys.width = minOf((w * 0.46f).toInt(), dp(240))
            resultsColumn.layoutParams = if (horizontal) horizontalResults else verticalResults
            keyboard.layoutParams = if (horizontal) horizontalKeys else verticalKeys
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

    private fun refresh() {
        val version = ++generation
        load?.cancel(true)
        searchTask?.cancel(true)
        grid.isEnabled = false
        retry.visibility = GONE
        status.setText(R.string.loading_apps)
        load = repository.load { result ->
            if (generation != version || !isAttachedToWindow) return@load
            result.fold(onSuccess = {
                snapshot = it
                search()
            }, onFailure = {
                status.setText(R.string.apps_failed)
                retry.visibility = VISIBLE
            })
        }
    }

    private fun changeQuery(value: String) {
        if (query == value) return
        query = value
        queryDisplay.textSize = if (value.isEmpty()) 14f else 22f
        queryDisplay.text = if (value.isEmpty()) context.getString(R.string.input_hint) else value
        queryDisplay.contentDescription = if (value.isEmpty()) context.getString(R.string.input_hint)
            else context.getString(R.string.input_query, value)
        keyboard.setHasInput(value.isNotEmpty())
        search()
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
            status.text = when {
                query.isNotEmpty() && found.isEmpty() -> context.getString(R.string.no_matches)
                query.isNotEmpty() -> context.getString(R.string.matches_count, found.size)
                found.isEmpty() -> context.getString(R.string.no_apps)
                else -> context.getString(R.string.apps_count, found.size)
            }
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
                setPadding(dp(2), dp(8), dp(2), dp(8))
                minimumHeight = dp(100)
                addView(ImageView(context).apply {
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LayoutParams(dp(48), dp(48)))
                addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(context.getColor(R.color.text_primary))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, 0)
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
                if (icon.tag == app.entry.id && generation == bindingGeneration && drawable != null) icon.setImageDrawable(drawable)
            }
            return cell
        }
    }
}

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

fun Context.heading(): TextView = TextView(this).apply {
    textSize = 28f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(getColor(R.color.text_primary))
    if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
}
