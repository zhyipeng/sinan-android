package dev.sinan.launcher.appearance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
//noinspection ExifInterface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class AppearanceImage(val requestCode: Int, val fileName: String, val maxEdge: Int) {
    PANEL(211, "panel-background.png", 2048),
    BALL(212, "floating-ball.png", 512),
}

/** 仅保存应用实际绘制的图像副本，主线程读取内存快照。 */
class AppearanceImageStore(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val images = mutableMapOf<AppearanceImage, Bitmap>()
    private val busy = mutableSetOf<AppearanceImage>()
    private val listeners = mutableSetOf<() -> Unit>()
    var isReady = false
        private set

    init {
        worker.execute {
            val loaded = AppearanceImage.entries.mapNotNull { kind ->
                val bitmap = runCatching { file(kind).openRead().use { BitmapFactory.decodeStream(it) } }.getOrNull()
                bitmap?.let { kind to it }
            }
            main.post {
                images.putAll(loaded)
                isReady = true
                notifyChanged()
            }
        }
    }

    operator fun get(kind: AppearanceImage): Bitmap? = images[kind]

    fun canEdit(kind: AppearanceImage): Boolean = isReady && kind !in busy

    fun import(kind: AppearanceImage, uri: Uri, onFinished: (Boolean) -> Unit) {
        edit(kind, onFinished) { file ->
            val bitmap = decode(uri, kind.maxEdge)
            val output = file.startWrite()
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException()
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            bitmap
        }
    }

    fun clear(kind: AppearanceImage, onFinished: (Boolean) -> Unit) {
        edit(kind, onFinished) { file ->
            file.delete()
            if (listOf("", ".new", ".bak").any { File(file.baseFile.path + it).exists() }) throw IOException()
            null
        }
    }

    private fun edit(
        kind: AppearanceImage,
        onFinished: (Boolean) -> Unit,
        operation: (AtomicFile) -> Bitmap?,
    ) {
        if (!busy.add(kind)) return
        notifyChanged()
        worker.execute {
            val result = runCatching { operation(file(kind)) }
            main.post {
                result.onSuccess { bitmap ->
                    if (bitmap == null) images.remove(kind) else images[kind] = bitmap
                }
                busy.remove(kind)
                notifyChanged()
                onFinished(result.isSuccess)
            }
        }
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }

    private fun file(kind: AppearanceImage) = AtomicFile(File(context.noBackupFilesDir, kind.fileName))

    private fun decode(uri: Uri, maxEdge: Int): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > maxEdge) {
                    val scale = maxEdge.toDouble() / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }
        return decodeLegacy(uri, maxEdge)
    }

    // API 26–27 使用平台 EXIF 处理拍摄方向，保持无第三方图像依赖。
    @SuppressLint("ExifInterface")
    private fun decodeLegacy(uri: Uri, maxEdge: Int): Bitmap {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val largest = maxOf(options.outWidth, options.outHeight)
        if (largest <= 0) throw IOException()
        var sample = 1
        while (largest / sample > maxEdge * 2) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException()

        val scale = (maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { transform.setRotate(90f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { transform.setRotate(-90f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(-90f)
        }
        if (transform.isIdentity) return scaled
        return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, transform, true).also {
            if (it !== scaled) scaled.recycle()
        }
    }
}
