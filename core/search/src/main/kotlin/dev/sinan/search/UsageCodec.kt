package dev.sinan.search

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.CRC32

/** 带版本和 CRC 的本地汇总格式；未知版本/损坏文件不能被默默覆盖。 */
object UsageCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAGIC = 0x534E5553
    private const val VERSION = 1

    fun encode(apps: List<AppUsage>): ByteArray {
        if (apps.size > UsageHistory.MAX_APPS || apps.sumOf { it.hourlyCounts.size } > UsageHistory.MAX_BUCKETS) throw IOException("Usage capacity exceeded")
        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        val ids = HashSet<AppId>()
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(apps.size)
        apps.sortedWith(compareBy<AppUsage> { it.appId.profileSerial }.thenBy { it.appId.packageName }.thenBy { it.appId.activityName }).forEach { app ->
            validate(app)
            if (!ids.add(app.appId)) throw IOException("Duplicate application")
            out.writeLong(app.appId.profileSerial)
            out.writeUTF(app.appId.packageName)
            out.writeUTF(app.appId.activityName)
            out.writeLong(app.lastRequestedAt)
            out.writeInt(app.hourlyCounts.size)
            app.hourlyCounts.toSortedMap().forEach { (hour, count) -> out.writeLong(hour); out.writeInt(count) }
        }
        out.flush()
        val body = buffer.toByteArray()
        out.writeLong(CRC32().apply { update(body) }.value)
        out.flush()
        return buffer.toByteArray().also { if (it.size > MAX_BYTES) throw IOException("Usage payload too large") }
    }

    fun decode(bytes: ByteArray): List<AppUsage> {
        if (bytes.size !in 20..MAX_BYTES) throw IOException("Invalid usage size")
        val bodySize = bytes.size - 8
        val expected = DataInputStream(ByteArrayInputStream(bytes, bodySize, 8)).readLong()
        if (CRC32().apply { update(bytes, 0, bodySize) }.value != expected) throw IOException("Invalid usage checksum")
        val input = DataInputStream(ByteArrayInputStream(bytes, 0, bodySize))
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Unsupported usage format")
        val size = input.readInt()
        if (size !in 0..UsageHistory.MAX_APPS) throw IOException("Invalid usage count")
        var buckets = 0
        val ids = HashSet<AppId>()
        val result = List(size) {
            val id = AppId(input.readLong(), input.readUTF(), input.readUTF())
            val last = input.readLong()
            val count = input.readInt()
            if (count !in 1..UsageHistory.RETENTION_HOURS) throw IOException("Invalid hour count")
            buckets += count
            if (buckets > UsageHistory.MAX_BUCKETS || !ids.add(id)) throw IOException("Invalid usage capacity")
            val hours = LinkedHashMap<Long, Int>()
            repeat(count) {
                val hour = input.readLong()
                if (hours.put(hour, input.readInt()) != null) throw IOException("Duplicate hour")
            }
            AppUsage(id, last, hours).also(::validate)
        }
        if (input.available() != 0) throw IOException("Unexpected usage data")
        return result
    }

    private fun validate(app: AppUsage) {
        if (!app.appId.isValidForUsage() || app.lastRequestedAt < 0 ||
            app.hourlyCounts.isEmpty() || app.hourlyCounts.size > UsageHistory.RETENTION_HOURS ||
            app.lastRequestedAt / UsageHistory.HOUR_MS !in app.hourlyCounts ||
            app.hourlyCounts.any { (h, n) -> h < 0 || h > app.lastRequestedAt / UsageHistory.HOUR_MS || n !in 1..UsageHistory.MAX_COUNT }) {
            throw IOException("Invalid usage summary")
        }
    }
}
