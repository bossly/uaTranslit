package ua.bossly.tools.translit

import android.content.Context
import java.util.Locale

/**
 * Created on 06.09.2020.
 * Copyright by oleg
 */
enum class WordPosition {
    BEGIN, MIDDLE, END
}

enum class TranslitDirection {
    CYRILLIC_TO_LATIN,
    LATIN_TO_CYRILLIC
}

interface WordTransform {
    fun snapSeparator(): String
    fun convert(char: Char, next: Char?, position: WordPosition): WordSnap
    fun isLosslessReverse(): Boolean = false
    fun convertReverseWord(word: String): String = word
    fun convertReverseToken(token: String): String = token
}

data class WordSnap(val snap: String, val skip: Boolean = false)

object WordTransformation {

    private val latinWordRegex =
        Regex("[A-Za-z\\u00C0-\\u024FїЇіІ]+(?:[ʹ'ʼ’][A-Za-z\\u00C0-\\u024FїЇіІ]*)*")
    private val morseWordSplitRegex = Regex("\\s*/\\s*|\\s{2,}")

    /**
     * Automatically detects whether [text] should be transliterated from Cyrillic -> Latin
     * or reverse-transliterated from Latin (or Morse) -> Cyrillic.
     */
    fun detectDirection(text: String, transform: WordTransform? = null): TranslitDirection {
        if (text.isBlank()) return TranslitDirection.CYRILLIC_TO_LATIN

        if (transform?.snapSeparator() == " ") {
            var hasMorseChars = false
            var hasLetters = false
            for (ch in text) {
                if (ch == '.' || ch == '-') {
                    hasMorseChars = true
                } else if (ch.isLetter()) {
                    hasLetters = true
                    break
                }
            }
            if (hasMorseChars && !hasLetters) {
                return TranslitDirection.LATIN_TO_CYRILLIC
            }
        }

        var latinCount = 0
        var cyrillicCount = 0

        for (ch in text) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '\u00C0'..'\u024F' || ch == 'ʹ' -> {
                    latinCount++
                }
                // Note: 'ї' and 'і' can appear in Manifest/Geographic Latin tokens alongside Latin letters,
                // so count unambiguous Cyrillic letters more strongly or count 'і'/'ї' as Cyrillic only when not outnumbered by Latin.
                ch in 'а'..'я' || ch in 'А'..'Я' || ch in "єЄґҐ" -> {
                    cyrillicCount++
                }
                ch in "іїІЇ" -> {
                    if (latinCount == 0) {
                        cyrillicCount++
                    }
                }
            }
        }

        return if (latinCount > cyrillicCount) {
            TranslitDirection.LATIN_TO_CYRILLIC
        } else {
            TranslitDirection.CYRILLIC_TO_LATIN
        }
    }

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

    fun transform(
        text: String,
        transform: WordTransform,
        direction: TranslitDirection,
        autocorrect: Boolean = true,
        context: Context? = null
    ): String {
        return when (direction) {
            TranslitDirection.CYRILLIC_TO_LATIN -> transform(text, transform)
            TranslitDirection.LATIN_TO_CYRILLIC -> transformReverse(
                text = text,
                transform = transform,
                autocorrect = autocorrect,
                context = context
            )
        }
    }

    /**
     * Reverse-transliterates Latin (or Morse) [text] into Ukrainian Cyrillic using the active [transform]
     * standard and optional offline on-device word [autocorrect].
     */
    fun transformReverse(
        text: String,
        transform: WordTransform,
        autocorrect: Boolean = true,
        context: Context? = null
    ): String {
        if (text.isEmpty()) return ""

        if (transform.snapSeparator() == " ") {
            return transformReverseMorse(text, transform, autocorrect, context)
        }

        val isLossless = transform.isLosslessReverse()
        val sb = StringBuilder(text.length)
        var lastIndex = 0

        for (match in latinWordRegex.findAll(text)) {
            sb.append(text, lastIndex, match.range.first)
            val originalWord = match.value
            val rawCyrillic = transform.convertReverseWord(originalWord)
            val finalLower = if (autocorrect) {
                UkrainianDictionary.correctWord(
                    rawCyrillic = rawCyrillic,
                    isLosslessStandard = isLossless,
                    enableTypoFallback = !isLossless,
                    context = context
                )
            } else {
                rawCyrillic
            }
            sb.append(applyCasePattern(originalWord, finalLower))
            lastIndex = match.range.last + 1
        }
        sb.append(text, lastIndex, text.length)
        return sb.toString()
    }

    private fun transformReverseMorse(
        text: String,
        transform: WordTransform,
        autocorrect: Boolean,
        context: Context?
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val wordGroups = morseWordSplitRegex.split(trimmed)
        val sb = StringBuilder()

        for (wIdx in wordGroups.indices) {
            if (wIdx > 0) sb.append(' ')
            val group = wordGroups[wIdx].trim()
            if (group.isEmpty()) continue
            val tokens = group.split(" ")
            val wordBuilder = StringBuilder(tokens.size)
            for (token in tokens) {
                if (token.isNotEmpty()) {
                    wordBuilder.append(transform.convertReverseToken(token))
                }
            }
            val rawWord = wordBuilder.toString()
            val finalWord = if (autocorrect && rawWord.length >= 2) {
                UkrainianDictionary.correctWord(
                    rawCyrillic = rawWord,
                    isLosslessStandard = true,
                    enableTypoFallback = false,
                    context = context
                )
            } else {
                rawWord
            }
            sb.append(finalWord)
        }

        return sb.toString()
    }

    fun applyCasePattern(original: String, transformedLower: String): String {
        if (original.isEmpty() || transformedLower.isEmpty()) return transformedLower
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return transformedLower

        val allUpper = letters.length > 1 && letters.all { it.isUpperCase() }
        if (allUpper) {
            return transformedLower.uppercase(Locale.getDefault())
        }

        if (original[0].isUpperCase()) {
            return transformedLower.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        return transformedLower
    }
}
