package dev.sinan.launcher.ball

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import dev.sinan.launcher.R
import dev.sinan.launcher.ui.dp
import kotlin.math.min

/** 同一套原生矢量绘制用于真实窗口和设置预览；48dp 触摸区域不随样式改变。 */
class BallStyleDrawable(context: Context, private val style: BallStyle) : Drawable() {
    private val size = context.dp(48)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brand = if (style == BallStyle.CLASSIC) context.getDrawable(R.drawable.ic_launcher_foreground)?.mutate() else null
    private var opacity = 255

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        val side = min(bounds.width(), bounds.height()).toFloat()
        canvas.translate(bounds.left + (bounds.width() - side) / 2, bounds.top + (bounds.height() - side) / 2)
        canvas.scale(side / 48f, side / 48f)
        if (opacity < 255) canvas.saveLayerAlpha(0f, 0f, 48f, 48f, opacity)
        paint.style = Paint.Style.FILL
        when (style) {
            BallStyle.CLASSIC -> {
                paint.color = BLUE; canvas.drawCircle(24f, 24f, 24f, paint)
                brand?.setBounds(0, 0, 48, 48); brand?.draw(canvas)
            }
            BallStyle.CURSOR -> {
                paint.color = LIGHT; canvas.drawCircle(24f, 24f, 23f, paint)
                paint.color = BLUE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawCircle(24f, 24f, 22.5f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawPath(cursor, paint)
            }
            BallStyle.POLARIS -> {
                paint.color = NAVY; canvas.drawCircle(24f, 24f, 24f, paint)
                paint.color = LIGHT; canvas.drawPath(star, paint)
                paint.color = GOLD; canvas.drawCircle(36f, 12f, 2f, paint)
            }
            BallStyle.JADE -> {
                paint.color = JADE; canvas.drawCircle(24f, 24f, 24f, paint)
                paint.color = GOLD; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f
                canvas.drawCircle(24f, 24f, 17.5f, paint)
                canvas.drawLine(24f, 7f, 24f, 10f, paint); canvas.drawLine(24f, 38f, 24f, 41f, paint)
                canvas.drawLine(7f, 24f, 10f, 24f, paint); canvas.drawLine(38f, 24f, 41f, 24f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawPath(needle, paint)
                paint.color = LIGHT; canvas.drawPath(needleTip, paint)
            }
            BallStyle.SEAL -> {
                paint.color = RED; canvas.drawRoundRect(1f, 1f, 47f, 47f, 12f, 12f, paint)
                paint.color = LIGHT; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
                canvas.drawRoundRect(6f, 6f, 42f, 42f, 8f, 8f, paint)
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                paint.textSize = 25f; paint.textAlign = Paint.Align.CENTER
                val baseline = 24f - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText("南", 24f, baseline, paint)
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private const val BLUE = 0xFF2954CE.toInt()
        private const val LIGHT = 0xFFEFF4FC.toInt()
        private const val NAVY = 0xFF18243C.toInt()
        private const val JADE = 0xFF143E38.toInt()
        private const val GOLD = 0xFFD7B77B.toInt()
        private const val RED = 0xFFB44432.toInt()
        private fun polygon(vararg points: Float) = Path().apply {
            moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            close()
        }
        private val cursor = polygon(34f, 13f, 26f, 35f, 21f, 27f, 13f, 22f)
        private val star = polygon(24f, 8f, 28f, 20f, 40f, 24f, 28f, 28f, 24f, 40f, 20f, 28f, 8f, 24f, 20f, 20f)
        private val needle = polygon(31f, 9f, 28f, 28f, 17f, 39f, 20f, 20f)
        private val needleTip = polygon(31f, 9f, 24f, 24f, 20f, 20f)
    }
}
