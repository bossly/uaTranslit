package ua.bossly.tools.translit

/**
 * Created on 06.09.2020.
 * Copyright by oleg
 */
enum class WordPosition {
    begin, middle, end
}

interface WordTranform {
    fun convert(char: Char, previous: Char, position: WordPosition): String
}

object WordTransformation {
    fun transform(text: String, transform: WordTranform): String {
        // 1. separate by words
        val words = text.split(" ")
        val transformed = ArrayList<String>()

        // 2. transform each word
        var previous = ' '

        for (word in words) {
            var chars = ArrayList<String>()
            word.forEachIndexed { index, char ->
                val position = when (index) {
                    0 -> WordPosition.begin
                    (word.length - 1) -> WordPosition.end
                    else -> WordPosition.middle
                }

                chars.add(transform.convert(char, previous, position))
            }

            transformed.add(chars.joinToString(""))
        }

        // 3. combine
        return transformed.joinToString(" ")
    }
}