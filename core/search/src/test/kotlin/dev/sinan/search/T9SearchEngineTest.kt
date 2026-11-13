package dev.sinan.search

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import kotlin.system.measureNanoTime

class T9SearchEngineTest {
    private fun app(name: String, key: String = name, aliases: List<String> = emptyList()) =
        AppEntry(AppId(0, key, "Main"), name, aliases = aliases)

    private val fixtures = listOf(
        app("微信"), app("支付宝"), app("抖音"), app("淘宝"), app("高德地图"), app("网易云音乐"),
        app("重庆银行"), app("Alipay"), app("QQ音乐"), app("铁路12306"), app("1Password"), app("绿色"),
    )

    @Test fun commonAppsAndMatchTiers() {
        val engine = T9SearchEngine(fixtures)
        val cases = listOf(
            Triple("99", "微信", MatchTier.INITIALS_EXACT),
            Triple("934946", "微信", MatchTier.FULL_PINYIN_EXACT),
            Triple("932", "支付宝", MatchTier.INITIALS_EXACT),
            Triple("94438226", "支付宝", MatchTier.FULL_PINYIN_EXACT),
            Triple("9443226", "支付宝", MatchTier.MIXED_PINYIN_PREFIX),
            Triple("39", "抖音", MatchTier.INITIALS_EXACT),
            Triple("82", "淘宝", MatchTier.INITIALS_EXACT),
            Triple("4338", "高德地图", MatchTier.INITIALS_EXACT),
            Triple("99999", "网易云音乐", MatchTier.INITIALS_EXACT),
            Triple("2794", "重庆银行", MatchTier.INITIALS_EXACT),
            Triple("254729", "Alipay", MatchTier.LATIN_NAME_EXACT),
            Triple("7799", "QQ音乐", MatchTier.INITIALS_EXACT),
            Triple("5873", "绿色", MatchTier.FULL_PINYIN_EXACT),
        )
        for ((query, name, tier) in cases) {
            val result = engine.search(query).first { it.app.label == name }
            assertEquals("$name / $query", tier, result.matchTier)
            assertTrue(result.isComplete)
        }
    }

    @Test fun numbersArePreservedIncludingZeroAndOne() {
        val engine = T9SearchEngine(fixtures)
        assertTrue(engine.search("12306").any { it.app.label == "铁路12306" })
        assertTrue(engine.search("1").any { it.app.label == "1Password" })
        assertTrue(engine.search("172779673").any { it.app.label == "1Password" && it.isComplete })
        assertTrue(engine.search("06").any { it.app.label == "铁路12306" })
        assertEquals("01029", encodeT9("010aZ"))
    }

    @Test fun mixedSearchCanStopWithinAnEdgeAndDistinguishesComplete() {
        val engine = T9SearchEngine(listOf(app("支付宝")))
        assertFalse(engine.search("9443").single().isComplete)
        assertTrue(engine.search("94432").single().isComplete) // zhi + f + b，不能错判为前缀。
        assertTrue(engine.search("9443226").single().isComplete)
        assertTrue(engine.search("938226").single().isComplete)
        assertTrue(engine.search("94438226").single().isComplete)
        assertTrue(engine.search("94432269").isEmpty())
    }

    @Test fun substringStartsAtSyllableBoundary() {
        val engine = T9SearchEngine(listOf(app("支付宝")))
        assertEquals(MatchTier.SYLLABLE_SUBSTRING, engine.search("38226").single().matchTier)
        assertTrue(engine.search("443").isEmpty()) // 不能从 zhi 的中间开始。
    }

    @Test fun exactMatchesCannotBeOverriddenByFrequency() {
        val engine = T9SearchEngine(fixtures)
        val scores = fixtures.associate { it.id to if (it.label == "网易云音乐") 1e12 else 0.0 }
        assertEquals("微信", engine.search("99", scores = scores).first().app.label)
        assertEquals("微信", engine.search("99", scores = mapOf(fixtures[0].id to Double.NaN)).first().app.label)
    }

    @Test fun duplicateIdsAndMultipleReadingsOnlyReturnOneBestHit() {
        val entry = app("重庆").copy(pinyinVariants = listOf(listOf("zhong", "qing"), listOf("chong", "qing")), aliases = listOf("cq"))
        val engine = T9SearchEngine(listOf(entry, entry.copy(aliases = listOf("重庆"))))
        val result = engine.search("27").single()
        assertEquals(MatchTier.CUSTOM_ALIAS_EXACT, result.matchTier)
        assertEquals(entry.id, result.app.id)
        assertTrue(engine.search("97").single().isComplete)
    }

    @Test fun tieBreakIsStableAcrossInputOrderAndProfilesRemainDistinct() {
        val a = app("Clock", "a")
        val b = app("Clock", "b")
        val work = a.copy(id = a.id.copy(profileSerial = 1))
        val forward = T9SearchEngine(listOf(a, b, work)).search("25625")
        val reverse = T9SearchEngine(listOf(work, b, a)).search("25625")
        assertEquals(listOf(a.id, b.id, work.id), forward.map { it.app.id })
        assertEquals(forward, reverse)
        assertEquals(b.id, T9SearchEngine(listOf(a, b)).search("25625", scores = mapOf(b.id to 1.0)).first().app.id)
    }

    @Test fun emptyInvalidAndBoundedQueries() {
        val engine = T9SearchEngine(fixtures)
        assertEquals(fixtures.map { it.id }, engine.search("", 100).map { it.app.id })
        assertTrue(engine.search("", 0).isEmpty())
        for (query in listOf("abc", " 99", "99\n", "９９", "9".repeat(33))) assertTrue(query, engine.search(query).isEmpty())
        assertEquals(2, engine.search("9", 2).size)
    }

    @Test fun diacriticsLocaleAndUnknownSymbolsDoNotCorruptEncoding() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val engine = T9SearchEngine(listOf(app("IDE"), app("Café"), app("微信🔒支付")))
            assertEquals("IDE", engine.search("433").first().app.label)
            assertEquals("Café", engine.search("2233").first().app.label)
            assertTrue(engine.search("9993").none { it.app.label == "微信🔒支付" })
            assertEquals(encodeT9("lv"), encodeT9("lǚ"))
            assertEquals(encodeT9("nv"), encodeT9("nu:"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun conversionOnlyHappensWhenBuildingIndexAndInputIsCopied() {
        var calls = 0
        val variants = mutableListOf(mutableListOf("wei", "xin"))
        val entries = mutableListOf(app("微信").copy(pinyinVariants = variants), app("Clock"))
        val provider = PinyinProvider { calls++; listOf(PhoneticReading(it.map(Char::toString))) }
        val engine = T9SearchEngine(entries, provider)
        val builtCalls = calls
        entries.clear()
        variants[0].clear()
        repeat(20) { assertTrue(engine.search("99").any { it.app.label == "微信" }) }
        assertEquals(builtCalls, calls)
    }

    @Test fun longNamesDoNotEnumerateMixedCombinations() {
        val entry = app("长名称").copy(pinyinVariants = listOf(List(128) { "chang" }))
        val engine = T9SearchEngine(listOf(entry))
        assertTrue(engine.search("242642".repeat(4)).isNotEmpty())
        assertTrue(engine.search("9".repeat(32)).isEmpty())
    }

    @Test fun benchmark300AppsReportsJvmCostWithoutDevicePerformanceClaims() {
        val apps = (0 until 300).map { index -> fixtures[index % fixtures.size].copy(id = AppId(0, "app.$index", "Main")) }
        lateinit var engine: T9SearchEngine
        val build = measureNanoTime { engine = T9SearchEngine(apps) }
        val queries = listOf("9", "99", "932", "94432", "254", "12306", "0", "99999999")
        repeat(100) { engine.search(queries[it % queries.size]) }
        val times = LongArray(400) { i -> measureNanoTime { engine.search(queries[i % queries.size]) } }.sorted()
        println("T9 JVM: apps=300 buildMs=${build / 1e6} searchP50Ms=${times[200] / 1e6} searchP95Ms=${times[380] / 1e6}")
        assertEquals(24, engine.search("99").size)
    }
}
