package dev.sinan.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import dev.sinan.search.AppEntry
import dev.sinan.search.AppId
import dev.sinan.search.T9SearchEngine
import java.text.Collator
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class LaunchableApp(val entry: AppEntry, val activity: LauncherActivityInfo)

class AppSnapshot(val apps: List<LaunchableApp>) {
    val index = T9SearchEngine(apps.map { it.entry })
    val byId = apps.associateBy { it.entry.id }
}

/** 当前资料的启动入口；包变更不改变已打开的网格快照。 */
class AppRepository(private val context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val worker = Executors.newFixedThreadPool(2)
    private val searchWorker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val icons = LruCache<AppId, Drawable.ConstantState>(48)
    @Volatile private var snapshot: AppSnapshot? = null
    @Volatile private var revision = 0

    init {
        launcher.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageAdded(name: String, user: UserHandle) = invalidate()
            override fun onPackageRemoved(name: String, user: UserHandle) = invalidate()
            override fun onPackageChanged(name: String, user: UserHandle) = invalidate()
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = invalidate()
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = invalidate()
        }, main)
    }

    fun invalidate() {
        revision++
        snapshot = null
        icons.evictAll()
    }

    fun load(callback: (Result<AppSnapshot>) -> Unit): Future<*>? {
        snapshot?.let { callback(Result.success(it)); return null }
        return worker.submit {
            val version = revision
            val result = runCatching {
                val user = Process.myUserHandle()
                val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
                // 排除 LauncherApps 为无启动入口的应用合成的详情页。
                val actual = context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
                ).map { android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet()
                val collator = Collator.getInstance()
                launcher.getActivityList(null, user)
                    .filter { it.componentName.packageName != context.packageName && it.componentName in actual }
                    .map { LaunchableApp(AppEntry(AppId(serial, it.componentName.packageName, it.componentName.className), it.label.toString()), it) }
                    .distinctBy { it.entry.id }
                    .sortedWith { a, b ->
                        collator.compare(a.entry.label, b.entry.label).takeIf { it != 0 }
                            ?: a.activity.componentName.flattenToString().compareTo(b.activity.componentName.flattenToString())
                    }.let(::AppSnapshot).also { if (version == revision) snapshot = it }
            }
            main.post { callback(result) }
        }
    }

    fun search(data: AppSnapshot, query: String, callback: (List<LaunchableApp>) -> Unit): Future<*> = searchWorker.submit {
        val results = if (query.isEmpty()) data.apps else data.index.search(query).mapNotNull { data.byId[it.app.id] }
        if (!Thread.currentThread().isInterrupted) main.post { callback(results) }
    }

    fun loadIcon(app: LaunchableApp, callback: (Drawable?) -> Unit) {
        icons.get(app.entry.id)?.let { callback(it.newDrawable(context.resources)); return }
        val version = revision
        worker.submit {
            val icon = runCatching { app.activity.getBadgedIcon(context.resources.displayMetrics.densityDpi) }.getOrNull()
            icon?.constantState?.let { if (version == revision) icons.put(app.entry.id, it) }
            main.post { callback(icon) }
        }
    }

    fun launch(app: LaunchableApp): Boolean = try {
        launcher.startMainActivity(app.activity.componentName, app.activity.user, null, null)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        invalidate(); false
    } catch (_: SecurityException) {
        invalidate(); false
    } catch (_: IllegalStateException) {
        invalidate(); false
    }
}
