package ua.bossly.tools.translit

/**
 * Created on 06.09.2020.
 * Copyright by oleg
 */
enum class WordPosition {
    BEGIN, MIDDLE, END
}

interface WordTransform {
    fun convert(char: Char, previous: Char, position: WordPosition): String
}

object WordTransformation {
    fun transform(text: String, transform: WordTransform): String {
        // 1. separate by words
        val words = text.split(" ")
        val transformed = ArrayList<String>()

        // 2. transform each word
        var previous = ' '

        for (word in words) {
            var chars = ArrayList<String>()
            word.forEachIndexed { index, char ->
                val position = when (index) {
                    0 -> WordPosition.BEGIN
                    (word.length - 1) -> WordPosition.END
                    else -> WordPosition.MIDDLE
                }

                chars.add(transform.convert(char, previous, position))
            }

            transformed.add(chars.joinToString(""))
        }

        // 3. combine words into sentence
        return transformed.joinToString(" ")
    }
}