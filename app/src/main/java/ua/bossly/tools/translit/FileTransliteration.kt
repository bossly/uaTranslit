package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream

/**
 * Created on 08.09.2020.
 * Copyright by oleg
 */
open class FileTransliteration(stream: InputStream) : WordTransform {
    val rows: List<List<String>> = csvReader().readAll(stream)

    override fun convert(char: Char, next: Char?, position: WordPosition): WordSnap {
        val lowercase = char.isLowerCase()
        val charLowercase = char.toString().toLowerCase()
        // rows index, where header = 0
        val origin = 1
        val translit = 2
        val start = rows.indexOfFirst {
            it[0] == "start"
        }

        val column = rows[origin].indexOf(charLowercase)

        if (column < 0) {
            return WordSnap(char.toString())
        }

        val combine = (charLowercase + next.toString().toLowerCase()).trim()
        var result = ""

        if (combine.length == 2) {
            val combIndex = rows[origin].indexOf(combine)

            if (start > 0 && combIndex >= 0 && rows[start][combIndex].isNotEmpty()) {
                result =
                    if (lowercase) rows[start][combIndex] else rows[start][combIndex].capitalize()
                return WordSnap(result, true)
            } else if (combIndex >= 0 && rows[translit][combIndex].isNotEmpty()) {
                result =
                    if (lowercase) rows[translit][combIndex] else rows[translit][combIndex].capitalize()
                return WordSnap(result, true)
            }
        }

        result = when (position) {
            WordPosition.BEGIN -> {
                if (start > 0 && rows[start][column].isNotEmpty()) {
                    if (lowercase) rows[start][column] else rows[start][column].capitalize()
                } else {
                    if (lowercase) rows[translit][column] else rows[translit][column].capitalize()
                }
            }
            else -> {
                if (lowercase) rows[translit][column] else rows[translit][column].capitalize()
            }
        }

        return WordSnap(result)
    }
}
