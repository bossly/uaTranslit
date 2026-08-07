package ua.bossly.tools.translit

/**
 * Created on 06.09.2020.
 * Copyright by oleg
 */
enum class WordPosition {
    BEGIN, MIDDLE, END
}

interface WordTransform {
    fun snapSeparator(): String
    fun convert(char: Char, next: Char?, position: WordPosition): WordSnap
}

data class WordSnap(val snap: String, val skip: Boolean = false)

object WordTransformation {
    fun transform(text: String, transform: WordTransform): String {
        if (text.isEmpty()) return ""

        val separator = transform.snapSeparator()
        val sb = StringBuilder(text.length * 2)
        val words = text.split(" ")

        for (wordIndex in words.indices) {
            if (wordIndex > 0) sb.append(' ')

            val word = words[wordIndex]
            var skip = false
            var firstChar = true

            word.forEachIndexed { index, char ->
                if (!skip) {
                    val position = when (index) {
                        0 -> WordPosition.BEGIN
                        (word.length - 1) -> WordPosition.END
                        else -> WordPosition.MIDDLE
                    }

                    val next = if (word.length > index + 1) word[index + 1] else null
                    val snap = transform.convert(char, next, position)

                    if (!firstChar && separator.isNotEmpty()) {
                        sb.append(separator)
                    }
                    sb.append(snap.snap)
                    firstChar = false
                    skip = snap.skip
                } else {
                    skip = false
                }
            }
        }

        return sb.toString()
    }
}