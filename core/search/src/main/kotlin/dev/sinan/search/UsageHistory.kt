package dev.sinan.search

/** 每个应用仅保留一个最近时间和稀疏小时计数，不保存逐次事件。 */
data class AppUsage(val appId: AppId, val lastRequestedAt: Long, val hourlyCounts: Map<Long, Int>)

/** 由持久化适配层串行使用。统计采用 UTC 小时，避免时区/夏令时破坏保留窗口。 */
class UsageHistory(initial: List<AppUsage> = emptyList()) {
    private val apps = LinkedHashMap<AppId, AppUsage>()
    var revision: Long = 0
        private set

    init {
        initial.forEach { value ->
            if (value.lastRequestedAt < 0 || !value.appId.isValidForUsage()) return@forEach
            val counts = value.hourlyCounts.filter { (hour, count) -> hour >= 0 && count > 0 && hour <= value.lastRequestedAt / HOUR_MS }
                .mapValues { it.value.coerceAtMost(MAX_COUNT) }
            if (counts.isEmpty() || value.lastRequestedAt / HOUR_MS !in counts) return@forEach
            val old = apps[value.appId]
            val merged = (old?.hourlyCounts.orEmpty().keys + counts.keys).associateWith { hour ->
                maxOf(old?.hourlyCounts?.get(hour) ?: 0, counts[hour] ?: 0)
            }
            apps[value.appId] = AppUsage(value.appId, maxOf(old?.lastRequestedAt ?: 0, value.lastRequestedAt), merged)
        }
    }

    fun record(appId: AppId, now: Long) {
        require(now >= 0)
        require(appId.isValidForUsage())
        prune(now)
        val hour = now / HOUR_MS
        val previous = apps[appId]
        val counts = previous?.hourlyCounts.orEmpty().toMutableMap()
        counts[hour] = ((counts[hour] ?: 0) + 1).coerceAtMost(MAX_COUNT)
        apps[appId] = AppUsage(appId, now, counts)
        revision++
        prune(now)
    }

    fun snapshot(now: Long): List<AppUsage> {
        prune(now)
        return apps.values.map { it.copy(hourlyCounts = it.hourlyCounts.toMap()) }
    }

    private fun prune(now: Long) {
        require(now >= 0)
        val currentHour = now / HOUR_MS
        val earliest = currentHour - RETENTION_HOURS + 1
        val cleaned = apps.values.filter { it.lastRequestedAt <= now }
            .mapNotNull { app ->
                val counts = app.hourlyCounts.filterKeys { it in earliest..currentHour }
                if (counts.isEmpty()) null else app.copy(hourlyCounts = counts)
            }.sortedByDescending { it.lastRequestedAt }.take(MAX_APPS)
        val keep = cleaned.flatMap { app -> app.hourlyCounts.keys.map { hour -> app.appId to hour } }
            .sortedByDescending { it.second }.take(MAX_BUCKETS).toSet()
        val next = cleaned.mapNotNull { app ->
            val counts = app.hourlyCounts.filterKeys { (app.appId to it) in keep }
            if (counts.isEmpty()) null else app.copy(hourlyCounts = counts)
        }.associateByTo(LinkedHashMap()) { it.appId }
        if (next != apps) {
            apps.clear()
            apps.putAll(next)
            revision++
        }
    }

    companion object {
        const val HOUR_MS = 3_600_000L
        const val RETENTION_HOURS = 30 * 24
        const val MAX_APPS = 1000
        const val MAX_BUCKETS = 20_000
        const val MAX_COUNT = 1_000_000
    }
}

internal fun AppId.isValidForUsage() = profileSerial >= 0 && packageName.isNotBlank() && packageName.length <= 512 &&
    activityName.isNotBlank() && activityName.length <= 1024
