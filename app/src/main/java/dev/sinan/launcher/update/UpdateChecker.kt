package dev.sinan.launcher.update

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.sinan.launcher.BuildConfig
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal class UpdateChecker(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    data class Release(val version: String, val downloadUri: Uri)

    /** 返回 null 表示本次自动检查仍在冷却期。 */
    fun check(manual: Boolean, onResult: (Result<Release?>?) -> Unit) {
        worker.execute {
            val preferences = context.getSharedPreferences("update-check", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val elapsed = now - preferences.getLong("last-attempt", 0)
            val result = if (!manual && elapsed in 0 until CHECK_INTERVAL_MS) {
                null
            } else {
                preferences.edit().putLong("last-attempt", now).apply()
                try {
                    Result.success(fetchLatest())
                } catch (error: IOException) {
                    Result.failure(error)
                } catch (error: JSONException) {
                    Result.failure(error)
                }
            }
            main.post { onResult(result) }
        }
    }

    private fun fetchLatest(): Release? {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            return when (connection.responseCode) {
                200 -> connection.inputStream.bufferedReader(Charsets.UTF_8).use { parseRelease(JSONObject(it.readText())) }
                404 -> null
                else -> throw IOException("GitHub Releases 请求失败")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(response: JSONObject): Release? {
        val tag = response.getString("tag_name")
        if (!VERSION_TAG.matches(tag) || response.optBoolean("prerelease") || response.optBoolean("draft")) {
            throw JSONException("发布版本无效")
        }
        val assets = response.getJSONArray("assets")
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("state") != "uploaded") continue
            val name = asset.getString("name")
            val code = APK_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (code <= BuildConfig.VERSION_CODE) return null
            return Release(tag.removePrefix("v"), Uri.parse("https://github.com/$REPOSITORY/releases/download/$tag/$name"))
        }
        throw JSONException("发布缺少 APK")
    }

    private companion object {
        const val REPOSITORY = "zhyipeng/sinan-android"
        val CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
        val VERSION_TAG = Regex("""v[0-9]+\.[0-9]+\.[0-9]+""")
        val APK_NAME = Regex("""sinan-([1-9][0-9]*)\.apk""")
    }
}
