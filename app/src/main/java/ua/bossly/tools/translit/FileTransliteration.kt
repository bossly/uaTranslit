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
            }
        }
    }

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
}

private fun String.caps(): String = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}
