package ua.bossly.tools.translit

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * On-device Android system SpellCheckerSession helper for general Ukrainian typo correction.
 *
 * Uses [TextServicesManager] with Ukrainian locale (`uk_UA`) to query the device's local
 * spellchecker for any words not already matched by the bundled [UkrainianDictionary].
 * Degrades gracefully when no Ukrainian system spellchecker is installed on the device.
 */
object SystemSpellCheckerHelper {

    private val ukrainianLocale: Locale = Locale.forLanguageTag("uk-UA")
    private val wordRegex = Regex("[А-ЯІЇЄҐа-яіїєґ']+")
    private val spellExecutor = Executors.newSingleThreadExecutor()

    suspend fun refineUnknownWords(context: Context, cyrillicText: String): String {
        if (cyrillicText.isBlank()) return cyrillicText

        val matches = wordRegex.findAll(cyrillicText).toList()
        if (matches.isEmpty()) return cyrillicText

        val unknownMatches = matches.filter { match ->
            val word = match.value
            word.length >= 3 && !UkrainianDictionary.isKnownWord(word, context)
        }
        if (unknownMatches.isEmpty()) return cyrillicText

        val tsm = context.applicationContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE)
                as? TextServicesManager ?: return cyrillicText

        val replacements = withTimeoutOrNull(450L) {
            querySystemSpellChecker(tsm, unknownMatches.map { it.value })
        } ?: return cyrillicText

        if (replacements.isEmpty()) return cyrillicText

        val sb = StringBuilder(cyrillicText.length)
        var lastIndex = 0
        for (match in matches) {
            sb.append(cyrillicText, lastIndex, match.range.first)
            val word = match.value
            val suggestion = replacements[word]
            if (suggestion != null) {
                sb.append(WordTransformation.applyCasePattern(word, suggestion))
            } else {
                sb.append(word)
            }
            lastIndex = match.range.last + 1
        }
        sb.append(cyrillicText, lastIndex, cyrillicText.length)
        return sb.toString()
    }

    private suspend fun querySystemSpellChecker(
        tsm: TextServicesManager,
        words: List<String>
    ): Map<String, String> = suspendCancellableCoroutine { cont ->
        var session: SpellCheckerSession? = null
        val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
            override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                val map = HashMap<String, String>()
                if (results != null) {
                    val count = minOf(words.size, results.size)
                    for (i in 0 until count) {
                        val info = results[i]
                        val attrs = info.suggestionsAttributes
                        val isInDict = (attrs and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                        if (!isInDict && info.suggestionsCount > 0) {
                            val candidate = info.getSuggestionAt(0)
                            if (!candidate.isNullOrBlank() && isCloseTypo(words[i], candidate)) {
                                map[words[i]] = candidate
                            }
                        }
                    }
                }
                try {
                    session?.close()
                } catch (_: Exception) {
                }
                if (cont.isActive) {
                    cont.resume(map)
                }
            }

            override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                val map = HashMap<String, String>()
                if (results != null && results.isNotEmpty()) {
                    val sentenceInfo = results[0]
                    val count = minOf(words.size, sentenceInfo.suggestionsCount)
                    for (i in 0 until count) {
                        val info = sentenceInfo.getSuggestionsInfoAt(i) ?: continue
                        val attrs = info.suggestionsAttributes
                        val isInDict = (attrs and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                        if (!isInDict && info.suggestionsCount > 0) {
                            val candidate = info.getSuggestionAt(0)
                            if (!candidate.isNullOrBlank() && isCloseTypo(words[i], candidate)) {
                                map[words[i]] = candidate
                            }
                        }
                    }
                }
                try {
                    session?.close()
                } catch (_: Exception) {
                }
                if (cont.isActive) {
                    cont.resume(map)
                }
            }
        }

        try {
            val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                .setLocale(ukrainianLocale)
                .setShouldReferToSpellCheckerLanguageSettings(false)
                .setSupportedAttributes(
                    SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY or
                            SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                            SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
                )
                .build()
            session = tsm.newSpellCheckerSession(params, spellExecutor, listener)
            if (session == null) {
                if (cont.isActive) cont.resume(emptyMap())
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try {
                    session.close()
                } catch (_: Exception) {
                }
            }
            val textInfos = words.map { TextInfo(it) }.toTypedArray()
            session.getSentenceSuggestions(textInfos, 1)
        } catch (_: Exception) {
            try {
                session?.close()
            } catch (_: Exception) {
            }
            if (cont.isActive) cont.resume(emptyMap())
        }
    }

    private fun isCloseTypo(original: String, suggestion: String): Boolean {
        val a = original.lowercase()
        val b = suggestion.lowercase()
        if (kotlin.math.abs(a.length - b.length) > 2) return false
        return levenshteinDistance(a, b) <= 2
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
