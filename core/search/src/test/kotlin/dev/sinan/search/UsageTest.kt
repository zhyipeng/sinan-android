package dev.sinan.search

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.zip.CRC32
import java.nio.ByteBuffer

class UsageTest {
    private val now = Instant.parse("2026-11-13T08:30:00Z").toEpochMilli()
    private val hour = now / UsageHistory.HOUR_MS
    private val a = AppId(0, "a", "Main")
    private val b = AppId(0, "b", "Main")
    private val zone = ZoneId.of("UTC")

    @Test fun aggregatesHourlyAndKeepsOneLatestTimePerApp() {
        val history = UsageHistory()
        history.record(a, now - 1000)
        history.record(a, now)
        history.record(a, now + UsageHistory.HOUR_MS)
        val app = history.snapshot(now + UsageHistory.HOUR_MS).single()
        assertEquals(mapOf(hour to 2, hour + 1 to 1), app.hourlyCounts)
        assertEquals(now + UsageHistory.HOUR_MS, app.lastRequestedAt)
    }

    @Test fun rollingRetentionDropsExpiredHoursAndFutureClockData() {
        val history = UsageHistory()
        history.record(a, now - 30 * 24 * UsageHistory.HOUR_MS)
        history.record(a, now - 29 * 24 * UsageHistory.HOUR_MS)
        history.record(a, now)
        assertEquals(2, history.snapshot(now).single().hourlyCounts.values.sum())
        history.record(b, now + UsageHistory.HOUR_MS)
        assertEquals(listOf(a), history.snapshot(now).map { it.appId })
        assertTrue(history.snapshot(now + 30 * 24 * UsageHistory.HOUR_MS).isEmpty())
    }

    @Test fun snapshotsAreDetachedAndRestorationIsIdempotent() {
        val mutable = mutableMapOf(hour to 2)
        val row = AppUsage(a, now, mutable)
        val history = UsageHistory(listOf(row, row))
        mutable.clear()
        assertEquals(2, history.snapshot(now).single().hourlyCounts[hour])
        val first = history.snapshot(now)
        history.record(a, now)
        assertEquals(2, first.single().hourlyCounts[hour])
        assertEquals(3, history.snapshot(now).single().hourlyCounts[hour])
        val restored = UsageHistory(UsageCodec.decode(UsageCodec.encode(history.snapshot(now))))
        assertEquals(history.snapshot(now), restored.snapshot(now))
    }

    @Test fun coldStartAndTiesKeepTheProvidedNameOrder() {
        val ids = listOf(b, a)
        assertTrue(UsageRanker.scores(ids, emptyList(), now, zone).isEmpty())
        assertEquals(ids, UsageRanker.order(ids, emptyMap()))
        assertEquals(ids, UsageRanker.order(ids, mapOf(a to Double.NaN)))
        assertEquals(listOf(a, b), UsageRanker.order(ids, mapOf(a to 0.1)))
    }

    @Test fun invalidIdsCannotPoisonHistoryAndSessionScoresStayFrozen() {
        val invalid = a.copy(profileSerial = -1)
        assertTrue(UsageHistory(listOf(AppUsage(invalid, now, mapOf(hour to 1)))).snapshot(now).isEmpty())
        val history = UsageHistory()
        assertThrows(IllegalArgumentException::class.java) { history.record(invalid, now) }
        history.record(a, now)
        val scores = UsageRanker.scores(listOf(a, b), history.snapshot(now), now, zone)
        history.record(b, now)
        assertEquals(listOf(a), scores.keys.toList())
        assertEquals(2, UsageRanker.scores(listOf(a, b), history.snapshot(now), now, zone).size)
    }

    @Test fun frequencyRecencyAndLongTermSignalsAreBounded() {
        val frequent = AppUsage(a, now, mapOf(hour to 6))
        val rare = AppUsage(b, now, mapOf(hour to 1))
        val scores = UsageRanker.scores(listOf(a, b), listOf(frequent, rare), now, zone)
        assertTrue(scores.getValue(a) > scores.getValue(b))
        assertTrue(scores.values.all { it.isFinite() && it in 0.0..1.0 })
        val older = AppUsage(b, now - 48 * UsageHistory.HOUR_MS, mapOf(hour - 48 to 1))
        val newer = UsageRanker.scores(listOf(a, b), listOf(rare.copy(appId = a), older), now, zone)
        assertTrue(newer.getValue(a) > newer.getValue(b))
        val longOnly = AppUsage(a, now - 20 * 24 * UsageHistory.HOUR_MS, mapOf(hour - 20 * 24 to 1))
        assertTrue(UsageRanker.scores(listOf(a), listOf(longOnly), now, zone).getValue(a) > 0)
    }

    @Test fun timeHabitsRequireEnoughSamples() {
        val morning = AppUsage(a, now, mapOf(hour - 24 to 10, hour to 1))
        val evening = AppUsage(b, now, mapOf(hour - 12 to 10, hour to 1))
        val learned = UsageRanker.scores(listOf(a, b), listOf(morning, evening), now, zone)
        assertTrue(learned.getValue(a) > learned.getValue(b))
        // 样本不足时不启用时段加分，较近的晚间记录应占优。
        val small = UsageRanker.scores(listOf(a, b), listOf(
            morning.copy(hourlyCounts = mapOf(hour - 24 to 1, hour to 1)),
            evening.copy(hourlyCounts = mapOf(hour - 12 to 1, hour to 1)),
        ), now, zone)
        assertTrue(small.getValue(b) > small.getValue(a))
    }

    @Test fun invisibleAppsAndExpiredOrFutureRecordsDoNotDistortScores() {
        val good = AppUsage(a, now, mapOf(hour to 1))
        val invisible = AppUsage(b, now, mapOf(hour to 100000))
        val scores = UsageRanker.scores(listOf(a), listOf(good, invisible), now, zone)
        assertEquals(UsageRanker.scores(listOf(a), listOf(good), now, zone), scores)
        assertTrue(UsageRanker.scores(listOf(a), listOf(good.copy(lastRequestedAt = now + 1)), now, zone).isEmpty())
        assertTrue(UsageRanker.scores(listOf(a), listOf(good.copy(hourlyCounts = mapOf(hour - 720 to 1))), now, zone).isEmpty())
    }

    @Test fun dstAndProfilesKeepSeparateBuckets() {
        val first = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()
        val second = Instant.parse("2026-11-01T06:30:00Z").toEpochMilli()
        val history = UsageHistory()
        history.record(a, first)
        history.record(a, second)
        history.record(a.copy(profileSerial = 1), second)
        val rows = history.snapshot(second)
        assertEquals(2, rows.size)
        assertEquals(2, rows.first { it.appId == a }.hourlyCounts.size)
        assertEquals(2, UsageRanker.scores(rows.map { it.appId }, rows, second, ZoneId.of("America/New_York")).size)
    }

    @Test fun learnedSearchCannotCrossMatchTiers() {
        val wechat = AppEntry(a, "微信")
        val music = AppEntry(b, "网易云音乐")
        val history = UsageHistory()
        repeat(100) { history.record(b, now) }
        val scores = UsageRanker.scores(listOf(a, b), history.snapshot(now), now, zone)
        assertEquals(a, T9SearchEngine(listOf(music, wechat)).search("99", scores = scores).first().app.id)
    }

    @Test fun codecRejectsCorruptionTruncationUnknownVersionsAndOversizedData() {
        val bytes = UsageCodec.encode(listOf(AppUsage(a, now, mapOf(hour to 2))))
        val changed = bytes.copyOf().apply { this[15] = (this[15].toInt() xor 1).toByte() }
        assertThrows(IOException::class.java) { UsageCodec.decode(changed) }
        assertThrows(IOException::class.java) { UsageCodec.decode(bytes.copyOf(bytes.size - 1)) }
        val future = bytes.copyOf().apply { this[7] = 2 }
        ByteBuffer.wrap(future, future.size - 8, 8).putLong(CRC32().apply { update(future, 0, future.size - 8) }.value)
        assertThrows(IOException::class.java) { UsageCodec.decode(future) }
        assertThrows(IOException::class.java) { UsageCodec.decode(ByteArray(UsageCodec.MAX_BYTES + 1)) }
        assertEquals(emptyList<AppUsage>(), UsageCodec.decode(UsageCodec.encode(emptyList())))
        val row = AppUsage(a, now, mapOf(hour to 1))
        assertThrows(IOException::class.java) { UsageCodec.encode(listOf(row, row)) }
    }

    @Test fun storageCapsAndCodecOrderAreDeterministic() {
        val hours = (0 until 720).associate { (hour - it) to 1 }
        val history = UsageHistory((0 until 30).map { AppUsage(AppId(0, "app.$it", "Main"), now, hours) })
        val rows = history.snapshot(now)
        assertEquals(UsageHistory.MAX_BUCKETS, rows.sumOf { it.hourlyCounts.size })
        assertArrayEquals(UsageCodec.encode(rows), UsageCodec.encode(rows.reversed()))
        val tooManyApps = UsageHistory((0..1000).map { AppUsage(AppId(0, "app.$it", "Main"), now, mapOf(hour to 1)) })
        assertEquals(1000, tooManyApps.snapshot(now).size)
    }
}
