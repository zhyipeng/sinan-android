package dev.sinan.launcher.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import dev.sinan.launcher.R
import dev.sinan.launcher.ui.dpF

/** 同一套居中填充规则，面板保留顶部圆角及主题遮罩，悬浮球裁成圆形。 */
class AppearanceImageDrawable(
    context: Context,
    private val bitmap: Bitmap,
    private val kind: AppearanceImage,
) : Drawable() {
    private val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = bitmapShader }
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.page_background) }
    private val tint = Paint(surface).apply { alpha = 179 }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.divider_subtle)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1)
    }
    private val panelRadius = context.dpF(28)
    private val shape = Path()
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val area = RectF(bounds)
        val matrix = Matrix()
        val scale = maxOf(area.width() / bitmap.width, area.height() / bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate(area.centerX() - bitmap.width * scale / 2, area.centerY() - bitmap.height * scale / 2)
        bitmapShader.setLocalMatrix(matrix)
        shape.reset()
        if (kind == AppearanceImage.BALL) {
            shape.addCircle(area.centerX(), area.centerY(), minOf(area.width(), area.height()) / 2, Path.Direction.CW)
        } else {
            val radius = panelRadius
            shape.addRoundRect(area, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
    }

    override fun draw(canvas: Canvas) {
        if (kind == AppearanceImage.BALL) {
            canvas.drawPath(shape, imagePaint)
            return
        }
        val layer = if (opacity < 255) {
            canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), opacity)
        } else null
        canvas.drawPath(shape, surface)
        canvas.drawPath(shape, imagePaint)
        canvas.drawPath(shape, tint)
        canvas.drawPath(shape, border)
        if (layer != null) canvas.restoreToCount(layer)
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha
        if (kind == AppearanceImage.BALL) imagePaint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = opacity

    override fun setColorFilter(colorFilter: ColorFilter?) {
        imagePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable API")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
