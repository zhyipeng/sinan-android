package dev.sinan.search

/** 建立一次后只读；按键路径不调用拼音转换器，也不展开 2^n 种混合路径。 */
class T9SearchEngine(entries: List<AppEntry>, provider: PinyinProvider = TinyPinyinProvider()) {
    private data class Path(
        val full: String,
        val initials: String,
        val units: List<String>,
        val han: Boolean,
        val alias: Boolean,
        val startsAtName: Boolean,
        val whole: Boolean,
    )
    private data class IndexedApp(val app: AppEntry, val paths: List<Path>)
    private data class Match(val tier: MatchTier, val remaining: Int)

    private val index: List<IndexedApp> = entries.groupBy { it.id }.map { (_, duplicates) ->
        val first = duplicates.first()
        val app = first.copy(pinyinVariants = first.pinyinVariants.map { it.toList() }, aliases = first.aliases.toList())
        val paths = duplicates.flatMap { entry ->
            val readings = if (entry.pinyinVariants.isEmpty()) provider.readings(entry.label)
                else entry.pinyinVariants.map { PhoneticReading(it) }
            val names = readings.mapNotNull { path(it, entry.label.any { c -> Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN }, false) }
            val aliases = entry.aliases.flatMap { alias ->
                provider.readings(alias).mapNotNull { path(it, alias.any { c -> Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN }, true) }
            }
            names + aliases
        }.distinct()
        IndexedApp(app, paths)
    }

    fun search(query: String, limit: Int = 24, scores: Map<AppId, Double> = emptyMap()): List<SearchResult> {
        require(limit >= 0) { "limit 不能为负数" }
        if (limit == 0 || query.length > MAX_QUERY_LENGTH || query.any { it !in '0'..'9' }) return emptyList()
        if (query.isEmpty()) return index.take(limit).map { SearchResult(it.app, MatchTier.BROWSE, 0.0) }
        return index.mapNotNull { indexed ->
            val match = indexed.paths.mapNotNull { match(it, query) }
                .minWithOrNull(compareBy<Match> { it.tier.ordinal }.thenBy { it.remaining }) ?: return@mapNotNull null
            val score = scores[indexed.app.id]?.takeIf { it.isFinite() } ?: 0.0
            Triple(indexed.app, match, score)
        }.sortedWith(
            compareBy<Triple<AppEntry, Match, Double>> { it.second.tier.ordinal }
                .thenByDescending { it.third }
                .thenBy { it.second.remaining }
                .thenBy { it.first.id.profileSerial }
                .thenBy { it.first.id.packageName }
                .thenBy { it.first.id.activityName },
        ).take(limit).map { (app, match, score) -> SearchResult(app, match.tier, score, match.remaining == 0) }
    }

    private fun path(reading: PhoneticReading, han: Boolean, alias: Boolean): Path? {
        if (reading.syllables.isEmpty() || reading.syllables.size > 256) return null
        val units = reading.syllables.map(::encodeT9)
        if (units.any { it.isEmpty() }) return null
        return Path(units.joinToString(""), units.joinToString("") { it.take(1) }, units, han, alias, reading.startsAtName, reading.coversWholeName)
    }

    private fun match(path: Path, query: String): Match? {
        val candidates = mutableListOf<Match>()
        fun add(tier: MatchTier, remaining: Int) {
            candidates.add(Match(if (path.startsAtName) tier else MatchTier.SYLLABLE_SUBSTRING, remaining))
        }
        if (path.whole) {
            when {
                path.alias && (query == path.full || query == path.initials) -> add(MatchTier.CUSTOM_ALIAS_EXACT, 0)
                path.han && query == path.initials -> add(MatchTier.INITIALS_EXACT, 0)
                path.han && query == path.full -> add(MatchTier.FULL_PINYIN_EXACT, 0)
                !path.han && query == path.full -> add(MatchTier.LATIN_NAME_EXACT, 0)
            }
        }
        if (path.initials.startsWith(query)) add(
            if (path.han && !path.alias) MatchTier.INITIALS_PREFIX else MatchTier.LATIN_OR_ALIAS_PREFIX,
            path.initials.length - query.length,
        )
        if (path.full.startsWith(query)) add(
            if (path.han && !path.alias) MatchTier.FULL_PINYIN_PREFIX else MatchTier.LATIN_OR_ALIAS_PREFIX,
            path.full.length - query.length,
        )
        mixedRemaining(path.units, query, 0)?.let {
            add(if (path.alias || !path.han) MatchTier.LATIN_OR_ALIAS_PREFIX else MatchTier.MIXED_PINYIN_PREFIX, it)
        }
        // 仅在音节/字符边界开始，不从一个拼音音节中间开始。
        for (start in 1 until path.units.size) {
            mixedRemaining(path.units, query, start)?.let { candidates.add(Match(MatchTier.SYLLABLE_SUBSTRING, it)) }
        }
        return candidates.minWithOrNull(compareBy<Match> { it.tier.ordinal }.thenBy { it.remaining })
    }

    /** 返回最佳路径剩余编码长度；0 为完整消费，>0 为前缀，null 为不匹配。 */
    private fun mixedRemaining(units: List<String>, query: String, start: Int): Int? {
        val memo = HashMap<Int, Int?>()
        val suffixLengths = IntArray(units.size + 1)
        for (i in units.indices.reversed()) suffixLengths[i] = suffixLengths[i + 1] + units[i].length
        fun visit(i: Int, j: Int): Int? {
            if (j == query.length) return suffixLengths[i]
            if (i == units.size) return null
            val key = i * (query.length + 1) + j
            if (memo.containsKey(key)) return memo[key]
            var best: Int? = null
            for (edge in listOf(units[i].take(1), units[i]).distinct()) {
                val length = minOf(edge.length, query.length - j)
                if (!query.regionMatches(j, edge, 0, length)) continue
                val remaining = if (j + length == query.length) edge.length - length + suffixLengths[i + 1]
                    else visit(i + 1, j + edge.length) ?: continue
                best = minOf(best ?: Int.MAX_VALUE, remaining)
            }
            memo[key] = best
            return best
        }
        return visit(start, 0)
    }

    companion object {
        const val MAX_QUERY_LENGTH = 32
    }
}
