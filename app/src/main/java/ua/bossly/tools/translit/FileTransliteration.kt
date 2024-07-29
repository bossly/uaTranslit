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

    override fun snapSeparator(): String = rows[0][3]

    override fun convert(char: Char, next: Char?, position: WordPosition): WordSnap {
        val lowercase = char.isLowerCase()
        val charLowercase = char.toString().lowercase(Locale.getDefault())
        // rows index, where header = 0
        val origin = 1
        val transliterated = 2
        val start = rows.indexOfFirst {
            it[0] == "start"
        }

        val column = rows[origin].indexOf(charLowercase)

        if (column < 0) {
            return WordSnap(char.toString())
        }

        val combine = (charLowercase + next.toString().lowercase(Locale.getDefault())).trim()
        var result = ""

        if (combine.length == 2) {
            val combIndex = rows[origin].indexOf(combine)

            if (start > 0 && combIndex >= 0 && rows[start][combIndex].isNotEmpty()) {
                result =
                    if (lowercase) rows[start][combIndex] else rows[start][combIndex].caps()
                return WordSnap(result, true)
            } else if (combIndex >= 0 && rows[transliterated][combIndex].isNotEmpty()) {
                result =
                    if (lowercase) rows[transliterated][combIndex] else rows[transliterated][combIndex].caps()
                return WordSnap(result, true)
            }
        }

        result = when (position) {
            WordPosition.BEGIN -> {
                if (start > 0 && rows[start][column].isNotEmpty()) {
                    if (lowercase) rows[start][column] else rows[start][column].caps()
                } else {
                    if (lowercase) rows[transliterated][column] else rows[transliterated][column].caps()
                }
            }
            else -> {
                if (lowercase) rows[transliterated][column] else rows[transliterated][column].caps()
            }
        }

        return WordSnap(result)
    }
}

private fun String.caps(): String = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}
