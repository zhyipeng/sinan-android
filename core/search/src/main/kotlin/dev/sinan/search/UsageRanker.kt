package dev.sinan.search

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln1p
import kotlin.math.pow

/** 无系统事件时只用工具内汇总；时钟和时区由调用方固定到面板会话。 */
object UsageRanker {
    fun scores(ids: Collection<AppId>, usage: List<AppUsage>, now: Long, zone: ZoneId): Map<AppId, Double> {
        val allowed = ids.toSet()
        val rows = usage.filter { it.appId in allowed && it.lastRequestedAt in 0..now }
        val hour = now / UsageHistory.HOUR_MS
        val currentSlot = slot(Instant.ofEpochMilli(now).atZone(zone).hour)
        data class Features(val id: AppId, val recent: Double, val recency: Double, val habit: Double, val total: Double, val mature: Boolean)
        val features = rows.mapNotNull { app ->
            val valid = app.hourlyCounts.filter { (h, count) -> h in (hour - 719)..hour && count > 0 }
            val total = valid.values.sumOf { it.toDouble() }
            if (total == 0.0) return@mapNotNull null
            val recent = valid.filterKeys { it > hour - 168 }.entries.sumOf { (h, count) ->
                val representative = minOf(now, h * UsageHistory.HOUR_MS + UsageHistory.HOUR_MS / 2)
                count * 2.0.pow(-(now - representative).toDouble() / (72 * UsageHistory.HOUR_MS))
            }
            val habit = if (total >= 10) valid.filterKeys { h ->
                slot(Instant.ofEpochMilli(h * UsageHistory.HOUR_MS).atZone(zone).hour) == currentSlot
            }.values.sumOf { it.toDouble() } / total else 0.0
            Features(app.appId, ln1p(recent), 2.0.pow(-(now - app.lastRequestedAt).toDouble() / (24 * UsageHistory.HOUR_MS)), habit, ln1p(total), total >= 10)
        }
        if (features.isEmpty()) return emptyMap()
        val recentMax = features.maxOf { it.recent }
        val totalMax = features.maxOf { it.total }
        val useHabit = features.any { it.mature }
        val weight = 0.35 + 0.25 + 0.10 + if (useHabit) 0.15 else 0.0
        return features.associate { f ->
            val recent = if (recentMax > 0) f.recent / recentMax else 0.0
            f.id to ((0.35 * recent + 0.25 * f.recency + 0.10 * f.total / totalMax + if (useHabit) 0.15 * f.habit else 0.0) / weight).coerceIn(0.0, 1.0)
        }
    }

    /** 同分保留调用方的名称顺序；搜索中的等级优先由 T9 引擎负责。 */
    fun order(ids: List<AppId>, scores: Map<AppId, Double>): List<AppId> = ids.withIndex()
        .sortedWith(compareByDescending<IndexedValue<AppId>> { scores[it.value]?.takeIf(Double::isFinite) ?: 0.0 }.thenBy { it.index })
        .map { it.value }

    private fun slot(hour: Int) = when (hour) {
        in 6..10 -> 0
        in 11..13 -> 1
        in 14..17 -> 2
        in 18..22 -> 3
        else -> 4
    }
}
