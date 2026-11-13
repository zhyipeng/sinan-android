package dev.sinan.launcher

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView

/** 立项骨架入口。悬浮窗口、权限引导与搜索交互尚未实现。 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            findViewById<TextView>(R.id.heading).isAccessibilityHeading = true
        }

        val content = findViewById<LinearLayout>(R.id.content)
        val padding = resources.getDimensionPixelSize(R.dimen.page_padding)
        content.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val edges = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                android.graphics.Rect(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                android.graphics.Rect(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            view.setPadding(
                padding + edges.left,
                padding + edges.top,
                padding + edges.right,
                padding + edges.bottom,
            )
            insets
        }
        content.requestApplyInsets()
    }
}
