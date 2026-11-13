package dev.sinan.search

import com.github.promeg.pinyinhelper.Pinyin
import java.text.Normalizer
import java.util.Locale

data class PhoneticReading(
    val syllables: List<String>,
    val startsAtName: Boolean = true,
    val coversWholeName: Boolean = true,
)

fun interface PinyinProvider {
    fun readings(label: String): List<PhoneticReading>
}

/** 词组优先，其他汉字使用 TinyPinyin 默认读音；不修改库的全局词典。 */
class TinyPinyinProvider : PinyinProvider {
    private val phrases = mapOf(
        "重庆" to listOf("chong", "qing"),
        "银行" to listOf("yin", "hang"),
        "音乐" to listOf("yin", "yue"),
        "长安" to listOf("chang", "an"),
        "长城" to listOf("chang", "cheng"),
        "厦门" to listOf("xia", "men"),
        "六安" to listOf("lu", "an"),
        "重装" to listOf("chong", "zhuang"),
    ).entries.sortedByDescending { it.key.length }

    override fun readings(label: String): List<PhoneticReading> {
        val text = normalizeLatin(label)
        val segments = mutableListOf<List<String>>()
        var units = mutableListOf<String>()
        var fragmented = false
        var startsAtName = true
        val starts = mutableListOf<Boolean>()
        fun flush() {
            if (units.isNotEmpty()) {
                segments.add(units.toList())
                starts.add(startsAtName)
                units = mutableListOf()
            }
        }
        var i = 0
        while (i < text.length) {
            val phrase = phrases.firstOrNull { text.startsWith(it.key, i) }
            if (phrase != null) {
                units.addAll(phrase.value)
                i += phrase.key.length
                continue
            }
            val char = text[i]
            when {
                char in 'a'..'z' || char in '0'..'9' -> units.add(char.toString())
                Pinyin.isChinese(char) -> units.add(normalizeLatin(Pinyin.toPinyin(char)))
                char.isWhitespace() || char in "-_.()'’" -> Unit
                else -> {
                    flush()
                    fragmented = true
                    startsAtName = false
                }
            }
            i++
        }
        flush()
        return segments.mapIndexed { index, syllables -> PhoneticReading(syllables, starts[index], !fragmented) }
    }
}

internal fun normalizeLatin(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace("u\u0308", "v")
    .replace("u:", "v")
    .replace(Regex("\\p{M}+"), "")

internal fun encodeT9(text: String): String = buildString {
    for (char in normalizeLatin(text)) append(when (char) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        in '0'..'9' -> char
        else -> return "" // 不把未知字符两侧静默拼接成完整名称。
    })
}
