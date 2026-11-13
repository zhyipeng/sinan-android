package dev.sinan.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
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
import java.util.concurrent.Future

class AppGridView @JvmOverloads constructor(context: Context, private val onLaunched: () -> Unit = {}) : LinearLayout(context) {
    private val repository = (context.applicationContext as SinanApplication).apps
    private val status = TextView(context)
    private val retry = Button(context)
    private val grid = GridView(context)
    private var apps = emptyList<LaunchableApp>()
    private var load: Future<*>? = null
    private var generation = 0
    private var launching = false
    private val adapter = AppAdapter()
    private fun dp(value: Int) = context.dp(value)

    init {
        orientation = VERTICAL
        status.setTextColor(context.getColor(R.color.text_secondary))
        status.textSize = 13f
        status.setPadding(dp(8), dp(12), dp(8), dp(16))
        addView(status)
        retry.setText(R.string.retry)
        retry.setOnClickListener { refresh() }
        retry.visibility = GONE
        addView(retry)
        grid.numColumns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = dp(4)
        grid.verticalSpacing = dp(8)
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            if (!launching) {
                val app = apps.getOrNull(position) ?: return@setOnItemClickListener
                launching = true
                if (repository.launch(app)) onLaunched() else {
                    launching = false
                    Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        generation++
        load?.cancel(true)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        val version = ++generation
        load?.cancel(true)
        retry.visibility = GONE
        status.setText(R.string.loading_apps)
        load = repository.load { result ->
            if (generation != version || !isAttachedToWindow) return@load
            result.fold(onSuccess = {
                apps = it
                adapter.notifyDataSetChanged()
                status.text = if (it.isEmpty()) context.getString(R.string.no_apps) else context.getString(R.string.apps_count, it.size)
                retry.visibility = if (it.isEmpty()) VISIBLE else GONE
            }, onFailure = {
                status.setText(R.string.apps_failed)
                retry.visibility = VISIBLE
            })
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
