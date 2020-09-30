package ua.bossly.tools.translit

/**
 * Created on 06.09.2020.
 * Copyright by oleg
 */
enum class WordPosition {
    BEGIN, MIDDLE, END
}

interface WordTransform {
    fun convert(char: Char, next: Char?, position: WordPosition): WordSnap
}

data class WordSnap(val snap: String, val skip: Boolean = false)

object WordTransformation {
    fun transform(text: String, transform: WordTransform): String {
        // 1. separate by words
        val words = text.split(" ")
        val transformed = ArrayList<String>()

        // 2. transform each word
        var next: Char?

        // data class WordSnap
        for (word in words) {
            val chars = ArrayList<String>()
            var skip = false
            word.forEachIndexed { index, char ->
                if (!skip) {
                    val position = when (index) {
                        0 -> WordPosition.BEGIN
                        (word.length - 1) -> WordPosition.END
                        else -> WordPosition.MIDDLE
                    }

                    next = if (word.length > index + 1) word[index + 1] else null
                    val snap = transform.convert(char, next, position)
                    chars.add(snap.snap)
                    skip = snap.skip
                } else {
                    skip = false
                }
            }

            transformed.add(chars.joinToString(""))
        }

        // 3. combine words into sentence
        return transformed.joinToString(" ")
    }
}