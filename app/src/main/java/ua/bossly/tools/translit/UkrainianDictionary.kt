package ua.bossly.tools.translit

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Offline on-device Ukrainian dictionary and disambiguator for Latin -> Cyrillic reverse transliteration.
 *
 * Uses a multi-level skeleton index built from a bundled ~95k Ukrainian lexicon (R.raw.uk_dictionary):
 * 1. Exact rank lookup (`exactRank`)
 * 2. Primary skeleton lookup (`primarySkeletonMap`): resolves lossy transliteration ambiguities
 *    such as omitted soft signs (`ь`), omitted apostrophes (`'`), `і`/`ї`/`й` collisions, and `г`/`ґ`.
 * 3. Loose skeleton lookup (`looseSkeletonMap`): resolves informal `и`/`і` and `е`/`є` swaps.
 * 4. SymSpell-style 1-edit-distance lookup (`deleteMap`) on high-frequency words for offline typo correction.
 * 5. Orthographic suffix rules for out-of-vocabulary adjectives/toponyms (`-ський`, `-цький`, `-зький`).
 */
object UkrainianDictionary {

    private const val TOP_TYPO_RANK_LIMIT = 15_000

    private data class DeleteEntry(
        val skeleton: String,
        val word: String,
        val rank: Int
    )

    private class DictionaryData(
        val exactRank: HashMap<String, Int>,
        val primarySkeletonMap: HashMap<String, String>,
        val looseSkeletonMap: HashMap<String, String>,
        val deleteMap: HashMap<String, DeleteEntry>
    )

    @Volatile
    private var loadedData: DictionaryData? = null

    fun ensureLoaded(context: Context? = null) {
        if (loadedData != null) return
        synchronized(this) {
            if (loadedData != null) return
            val stream: InputStream? = try {
                context?.applicationContext?.resources?.openRawResource(R.raw.uk_dictionary)
                    ?: UkrainianDictionary::class.java.getResourceAsStream("/uk_dictionary.txt")
            } catch (_: Exception) {
                UkrainianDictionary::class.java.getResourceAsStream("/uk_dictionary.txt")
            }
            loadedData = if (stream != null) {
                stream.use { parseStream(it) }
            } else {
                DictionaryData(HashMap(), HashMap(), HashMap(), HashMap())
            }
        }
    }

    private fun parseStream(stream: InputStream): DictionaryData {
        val exactRank = HashMap<String, Int>(128_000)
        val primarySkeletonMap = HashMap<String, String>(128_000)
        val looseSkeletonMap = HashMap<String, String>(128_000)
        val deleteMap = HashMap<String, DeleteEntry>(128_000)

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            var index = 0
            for (rawLine in lines) {
                val word = rawLine.trim()
                if (word.isEmpty()) continue
                if (!exactRank.containsKey(word)) {
                    exactRank[word] = index
                }
                val ps = primarySkeleton(word)
                if (!primarySkeletonMap.containsKey(ps)) {
                    primarySkeletonMap[ps] = word
                }
                val ls = looseSkeletonFromPrimary(ps)
                if (!looseSkeletonMap.containsKey(ls)) {
                    looseSkeletonMap[ls] = word
                }
                if (index < TOP_TYPO_RANK_LIMIT && ps.length >= 4) {
                    for (i in ps.indices) {
                        val deleted = ps.removeRange(i, i + 1)
                        if (!deleteMap.containsKey(deleted)) {
                            deleteMap[deleted] = DeleteEntry(ps, word, index)
                        }
                    }
                }
                index++
            }
        }

        return DictionaryData(exactRank, primarySkeletonMap, looseSkeletonMap, deleteMap)
    }

    /**
     * Computes the primary Cyrillic skeleton of a word by stripping `ь` and apostrophes,
     * and normalizing `ї`/`й` -> `і` and `ґ` -> `г`.
     */
    fun primarySkeleton(word: String): String {
        val sb = StringBuilder(word.length)
        for (i in word.indices) {
            when (val ch = word[i].lowercaseChar()) {
                'ь', '\'', 'ʼ', '’', 'ʹ' -> continue
                'ї', 'й' -> sb.append('і')
                'ґ' -> sb.append('г')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun looseSkeletonFromPrimary(primary: String): String {
        val sb = StringBuilder(primary.length)
        for (i in primary.indices) {
            when (val ch = primary[i]) {
                'и' -> sb.append('і')
                'є' -> sb.append('е')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isKnownWord(word: String, context: Context? = null): Boolean {
        ensureLoaded(context)
        val data = loadedData ?: return false
        val lower = word.lowercase()
        return data.exactRank.containsKey(lower)
    }

    /**
     * Resolves transliteration ambiguities and common 1-edit-distance typos for a lowercase or mixed-case Cyrillic word.
     */
    fun correctWord(
        rawCyrillic: String,
        isLosslessStandard: Boolean = false,
        enableTypoFallback: Boolean = true,
        context: Context? = null
    ): String {
        if (rawCyrillic.length < 2) return rawCyrillic
        ensureLoaded(context)
        val data = loadedData ?: return applyOrthographicSuffixes(rawCyrillic)

        val lower = rawCyrillic.lowercase()
        val rawRank = data.exactRank[lower]
        if (isLosslessStandard && rawRank != null) {
            return lower
        }

        val ps = primarySkeleton(lower)
        val bestPrimary = data.primarySkeletonMap[ps]

        if (rawRank != null) {
            if (!isLosslessStandard && bestPrimary != null) {
                val bestRank = data.exactRank[bestPrimary] ?: Int.MAX_VALUE
                if (bestRank < rawRank) {
                    return bestPrimary
                }
            }
            return lower
        }

        if (bestPrimary != null) {
            return bestPrimary
        }

        val ls = looseSkeletonFromPrimary(ps)
        val bestLoose = data.looseSkeletonMap[ls]
        if (bestLoose != null) {
            return bestLoose
        }

        // Apply orthographic suffix normalization (e.g. -ский -> -ський) and re-check dictionary
        val suffixNormalized = applyOrthographicSuffixes(lower)
        if (suffixNormalized != lower) {
            if (data.exactRank.containsKey(suffixNormalized)) {
                return suffixNormalized
            }
            val normPs = primarySkeleton(suffixNormalized)
            data.primarySkeletonMap[normPs]?.let { return it }
        }

        if (enableTypoFallback && ps.length >= 4) {
            findTypoCorrection(ps, data)?.let { return it }
        }

        return suffixNormalized
    }

    private fun findTypoCorrection(ps: String, data: DictionaryData): String? {
        var bestRank = Int.MAX_VALUE
        var bestWord: String? = null

        // 1. Single extra character in input (deleting 1 char from input ps matches primarySkeletonMap)
        for (i in ps.indices) {
            val deleted = ps.removeRange(i, i + 1)
            val match = data.primarySkeletonMap[deleted]
            if (match != null && match[0] == ps[0]) {
                val rank = data.exactRank[match] ?: Int.MAX_VALUE
                if (rank < TOP_TYPO_RANK_LIMIT && rank < bestRank) {
                    bestRank = rank
                    bestWord = match
                }
            }
        }

        // 2. Single omitted character in input (input ps itself is in deleteMap)
        data.deleteMap[ps]?.let { entry ->
            if (entry.skeleton[0] == ps[0] && entry.rank < bestRank) {
                bestRank = entry.rank
                bestWord = entry.word
            }
        }

        // 3. Adjacent character transposition
        for (i in 0 until ps.length - 1) {
            val sb = StringBuilder(ps)
            val tmp = sb[i]
            sb[i] = sb[i + 1]
            sb[i + 1] = tmp
            val swapped = sb.toString()
            val match = data.primarySkeletonMap[swapped]
            if (match != null && match[0] == ps[0]) {
                val rank = data.exactRank[match] ?: Int.MAX_VALUE
                if (rank < TOP_TYPO_RANK_LIMIT && rank < bestRank) {
                    bestRank = rank
                    bestWord = match
                }
            }
        }

        // 4. Single character substitution (at position >= 1 to preserve initial letter)
        if (bestWord == null) {
            for (i in 1 until ps.length) {
                val deleted = ps.removeRange(i, i + 1)
                val entry = data.deleteMap[deleted]
                if (entry != null &&
                    entry.skeleton.length == ps.length &&
                    entry.skeleton[0] == ps[0] &&
                    entry.skeleton.removeRange(i, i + 1) == deleted &&
                    entry.rank < bestRank
                ) {
                    bestRank = entry.rank
                    bestWord = entry.word
                }
            }
        }

        return bestWord
    }

    /**
     * Fallback Ukrainian orthographic rules for words not present in the dictionary
     * (e.g. rare surnames or toponyms with `-ський`, `-цький`, `-зький`, `-цьк`, `-ськ`).
     */
    private fun applyOrthographicSuffixes(word: String): String {
        val replacements = listOf(
            "ский" to "ський",
            "ський" to "ський",
            "цкий" to "цький",
            "зкий" to "зький",
            "ского" to "ського",
            "скому" to "ському",
            "ским" to "ським",
            "ских" to "ських",
            "цкого" to "цького",
            "цкому" to "цькому",
            "цким" to "цьким",
            "цких" to "цьких",
            "цьк" to "цьк",
            "нецк" to "нецьк",
            "луцк" to "луцьк",
            "івск" to "івськ",
            "овск" to "овськ",
            "анск" to "анськ",
            "енск" to "енськ"
        )
        for ((suffix, target) in replacements) {
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                return word.dropLast(suffix.length) + target
            }
        }
        return word
    }
}
