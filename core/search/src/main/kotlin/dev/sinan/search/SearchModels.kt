package dev.sinan.search

/** profileSerial 由 Android 适配层提供；搜索模块不依赖 Android 类型。 */
data class AppId(
    val profileSerial: Long,
    val packageName: String,
    val activityName: String,
)

/** 一个快照中的可启动入口；图标由 UI 层独立缓存。 */
data class AppEntry(
    val id: AppId,
    val label: String,
    val pinyinVariants: List<List<String>> = emptyList(),
    val aliases: List<String> = emptyList(),
)

/** 按优先级从高到低声明；使用分数不能跨越匹配等级。 */
enum class MatchTier {
    CUSTOM_ALIAS_EXACT,
    INITIALS_EXACT,
    FULL_PINYIN_EXACT,
    LATIN_NAME_EXACT,
    INITIALS_PREFIX,
    FULL_PINYIN_PREFIX,
    MIXED_PINYIN_PREFIX,
    LATIN_OR_ALIAS_PREFIX,
    SYLLABLE_SUBSTRING,
    BROWSE,
}

data class SearchResult(
    val app: AppEntry,
    val matchTier: MatchTier,
    val personalizedScore: Double,
    val isComplete: Boolean = false,
)
