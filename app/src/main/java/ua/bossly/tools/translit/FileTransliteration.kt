package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream

/**
 * Created on 08.09.2020.
 * Copyright by oleg
 */
class FileTransliteration : WordTranform {

    val rows: List<List<String>>

    constructor(stream: InputStream) {
        rows = csvReader().readAll(stream)
    }

    constructor(string: String) {
        rows = csvReader().readAll(string)
    }

    override fun convert(char: Char, previous: Char, position: WordPosition): String {
        val lowercase = char.isLowerCase()
        val charLowercase = char.toString().toLowerCase()

        return when (position) {
            else -> {
                val column = rows[0].indexOf(charLowercase)
                return if (column >= 0) {
                    if (lowercase) rows[1][column] else rows[1][column].capitalize()
                } else {
                    char.toString()
                }
            }
        }
    }
}
