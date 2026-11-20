package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream
import java.util.Locale

/**
 * Created on 08.09.2020.
 * Copyright by oleg
 */
open class FileTransliteration(stream: InputStream) : WordTransform {
    val rows: List<List<String>> = csvReader().readAll(stream)

    private val cachedSeparator: String = rows.getOrNull(0)?.getOrNull(3) ?: ""
    override fun snapSeparator(): String = cachedSeparator

    private val singleCharDefault = HashMap<Char, String>()
    private val singleCharStart = HashMap<Char, String>()
    private val twoCharDefault = HashMap<String, String>()
    private val twoCharStart = HashMap<String, String>()
    private val originChars = HashSet<Char>()

    private val reverseDefaultMap = HashMap<String, String>()
    private val reverseStartMap = HashMap<String, String>()
    private val losslessReverse: Boolean

    init {
        val originRow = rows.getOrNull(1)
        val defaultRow = rows.getOrNull(2)
        val startRowIndex = rows.indexOfFirst { it.isNotEmpty() && it[0] == "start" }
        val startRow = if (startRowIndex > 0) rows[startRowIndex] else null

        if (originRow != null && defaultRow != null) {
            val maxCol = minOf(originRow.size, defaultRow.size)
            for (i in 1 until maxCol) {
                val orig = originRow[i]
                if (orig.isEmpty()) continue

                val defVal = defaultRow.getOrNull(i) ?: ""
                val startVal = startRow?.getOrNull(i) ?: ""

                if (orig.length == 1) {
                    val keyChar = orig[0].lowercaseChar()
                    originChars.add(keyChar)
                    singleCharDefault[keyChar] = defVal
                    if (startVal.isNotEmpty()) {
                        singleCharStart[keyChar] = startVal
                    }
                } else if (orig.length == 2) {
                    val keyStr = orig.lowercase(Locale.getDefault())
                    twoCharDefault[keyStr] = defVal
                    if (startVal.isNotEmpty()) {
                        twoCharStart[keyStr] = startVal
                    }
                }

                val origLower = orig.trim().lowercase(Locale.getDefault())
                val defLower = defVal.trim().lowercase(Locale.getDefault())
                val startLower = startVal.trim().lowercase(Locale.getDefault())

                if (defLower.isNotEmpty()) {
                    reverseDefaultMap.putIfAbsent(defLower, origLower)
                    // Normalize Cyrillic 'і' inside Geographic 1996 'іu' -> ASCII 'iu'
                    val normalizedDef = defLower.replace('і', 'i')
                    reverseDefaultMap.putIfAbsent(normalizedDef, origLower)
                }
                if (startLower.isNotEmpty()) {
                    reverseStartMap.putIfAbsent(startLower, origLower)
                }
            }
        }

        losslessReverse = reverseDefaultMap.containsKey("ì") || reverseDefaultMap.containsKey("ś")
    }

    override fun isLosslessReverse(): Boolean = losslessReverse

    override fun convert(char: Char, next: Char?, position: WordPosition): WordSnap {
        val lowercase = char.isLowerCase()
        val charLower = char.lowercaseChar()

        if (next != null) {
            val combine = "$charLower${next.lowercaseChar()}".trim()
            if (combine.length == 2) {
                val startRes = twoCharStart[combine]
                if (startRes != null && startRes.isNotEmpty()) {
                    val res = if (lowercase) startRes else startRes.caps()
                    return WordSnap(res, true)
                }
                val defRes = twoCharDefault[combine]
                if (defRes != null && defRes.isNotEmpty()) {
                    val res = if (lowercase) defRes else defRes.caps()
                    return WordSnap(res, true)
                }
            }
        }

        if (!originChars.contains(charLower)) {
            return WordSnap(char.toString())
        }

        val resultStr = when (position) {
            WordPosition.BEGIN -> {
                val startRes = singleCharStart[charLower]
                if (startRes != null && startRes.isNotEmpty()) {
                    startRes
                } else {
                    singleCharDefault[charLower] ?: ""
                }
            }
            else -> {
                singleCharDefault[charLower] ?: ""
            }
        }

        val finalResult = if (lowercase) resultStr else resultStr.caps()
        return WordSnap(finalResult)
    }

    override fun convertReverseToken(token: String): String {
        return reverseDefaultMap[token.lowercase(Locale.getDefault())] ?: token
    }

    override fun convertReverseWord(word: String): String {
        if (word.isEmpty()) return ""
        val wLow = word.lowercase(Locale.getDefault())
        val n = wLow.length
        var i = 0
        val sb = StringBuilder(n)

        while (i < n) {
            val isStart = (i == 0)

            // Lookahead: 'iia', 'iie', 'iiu' -> 'і' + ('ia'/'ie'/'iu')
            if (wLow[i] == 'i' && i + 2 < n) {
                val nextTwo = wLow.substring(i + 1, i + 3)
                if ((nextTwo == "ia" || nextTwo == "ie" || nextTwo == "iu") &&
                    reverseDefaultMap.containsKey(nextTwo)
                ) {
                    sb.append(reverseDefaultMap["i"] ?: "і")
                    i += 1
                    continue
                }
            }

            // Lookahead at word end: 'ii' -> 'ій', 'yi' -> 'ий', 'iy' -> 'ій'
            if (i + 2 == n && i > 0) {
                val pair = wLow.substring(i, i + 2)
                if (pair == "ii" && reverseDefaultMap["i"] == "і") {
                    sb.append("ій")
                    i += 2
                    continue
                }
                if (pair == "yi" && reverseDefaultMap["y"] == "и") {
                    sb.append("ий")
                    i += 2
                    continue
                }
                if (pair == "iy" && reverseDefaultMap["i"] == "і" && reverseDefaultMap["y"] == "и") {
                    sb.append("ій")
                    i += 2
                    continue
                }
            }

            var matched = false
            for (length in 4 downTo 1) {
                if (i + length <= n) {
                    val sub = wLow.substring(i, i + length)
                    if (isStart) {
                        val startMatch = reverseStartMap[sub]
                        if (startMatch != null) {
                            sb.append(startMatch)
                            i += length
                            matched = true
                            break
                        }
                    }
                    val defMatch = reverseDefaultMap[sub]
                    if (defMatch != null) {
                        // In American 1965, "'" maps to 'ь' unless before an iotated vowel (ye/yi/yu/ya) where it is an apostrophe
                        if (sub == "'" && i + 1 < n) {
                            val rem = wLow.substring(i + 1)
                            if (IOTATED_PREFIXES.any { rem.startsWith(it) }) {
                                sb.append('\'')
                            } else {
                                sb.append(defMatch)
                            }
                        } else {
                            sb.append(defMatch)
                        }
                        i += length
                        matched = true
                        break
                    }
                }
            }

            if (!matched) {
                for (length in 4 downTo 1) {
                    if (i + length <= n) {
                        val sub = wLow.substring(i, i + length)
                        val fallback = INFORMAL_LATIN_FALLBACKS[sub]
                        if (fallback != null) {
                            sb.append(fallback)
                            i += length
                            matched = true
                            break
                        }
                    }
                }
            }

            if (!matched) {
                val ch = wLow[i]
                if (ch == 'ʼ' || ch == '’') {
                    sb.append('\'')
                } else {
                    sb.append(ch)
                }
                i += 1
            }
        }

        return sb.toString()
    }

    companion object {
        private val IOTATED_PREFIXES = listOf(
            "ye", "yi", "yu", "ya", "ie", "ia", "iu", "je", "ju", "ja", "ї"
        )

        private val INFORMAL_LATIN_FALLBACKS = mapOf(
            "shch" to "щ",
            "sch" to "щ",
            "zgh" to "зг",
            "zh" to "ж",
            "kh" to "х",
            "ts" to "ц",
            "ch" to "ч",
            "sh" to "ш",
            "ye" to "є",
            "ie" to "є",
            "yi" to "ї",
            "yu" to "ю",
            "iu" to "ю",
            "ya" to "я",
            "ia" to "я",
            "je" to "є",
            "ju" to "ю",
            "ja" to "я",
            "jo" to "йо",
            "yo" to "йо",
            "j" to "й",
            "c" to "ц",
            "w" to "в",
            "x" to "кс",
            "q" to "к"
        )
    }
}

private fun String.caps(): String = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}
