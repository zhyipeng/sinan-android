package dev.sinan.launcher.usage

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.widget.Toast
import dev.sinan.launcher.R
import dev.sinan.search.AppId
import dev.sinan.search.AppUsage
import dev.sinan.search.UsageCodec
import dev.sinan.search.UsageHistory
import dev.sinan.search.UsageRanker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.Executors

/** 全部文件操作和统计在单线程执行，回调也在此线程；UI 适配层负责发布到主线程。 */
class LocalUsageRepository(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    // noBackupFilesDir getter 可能创建目录，延迟到后台线程首次使用。
    private val file by lazy { File(context.noBackupFilesDir, "usage-summary.bin") }
    private val atomic by lazy { AtomicFile(file) }
    private var history: UsageHistory? = null
    private var savedRevision = 0L
    private var broken = false
    private var warned = false

    fun scores(ids: List<AppId>, callback: (Map<AppId, Double>) -> Unit) {
        worker.execute {
            val now = System.currentTimeMillis()
            val scores = try {
                val state = load()
                val rows = state.snapshot(now)
                persist(state, rows)
                UsageRanker.scores(ids, rows, now, ZoneId.systemDefault())
            } catch (_: Exception) {
                warnOnce()
                emptyMap()
            }
            callback(scores)
        }
    }

    fun record(appId: AppId, requestedAt: Long = System.currentTimeMillis()) {
        worker.execute {
            try {
                val state = load()
                state.record(appId, requestedAt)
                persist(state, state.snapshot(requestedAt))
            } catch (_: Exception) {
                warnOnce()
            }
        }
    }

    fun clear(callback: (Result<Unit>) -> Unit) {
        worker.execute {
            callback(runCatching {
                deleteFile()
                history = UsageHistory()
                savedRevision = 0
                broken = false
                warned = false
            })
        }
    }

    private fun load(): UsageHistory {
        history?.let { return it }
        if (broken) throw IOException("Usage storage unavailable")
        try {
            val bytes = atomic.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    if (output.size() + size > UsageCodec.MAX_BYTES) throw IOException("Usage storage too large")
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
            return UsageHistory(UsageCodec.decode(bytes)).also { history = it; savedRevision = it.revision }
        } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) {
                broken = true
                throw error
            }
            deleteFile() // 丢弃首次写入被中断后留下的未提交 .new 文件。
            return UsageHistory().also { history = it }
        } catch (error: Exception) {
            // 未知版本或损坏的数据不被新记录覆盖，用户可明确清空后恢复。
            broken = true
            throw error
        }
    }

    private fun persist(state: UsageHistory, rows: List<AppUsage>) {
        if (state.revision == savedRevision) return
        if (rows.isEmpty()) {
            deleteFile()
        } else {
            val bytes = UsageCodec.encode(rows)
            val stream = atomic.startWrite()
            try {
                stream.write(bytes)
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
        }
        savedRevision = state.revision
    }

    private fun deleteFile() {
        atomic.delete()
        if (listOf(file, File(file.path + ".bak"), File(file.path + ".new")).any { it.exists() }) throw IOException("Usage removal failed")
    }

    private fun warnOnce() {
        if (warned) return
        warned = true
        main.post { Toast.makeText(context, R.string.usage_unavailable, Toast.LENGTH_LONG).show() }
    }
}
