@file:Suppress("unused")

package ua.bossly.tools.translit.appfunctions

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ua.bossly.tools.translit.MainActivity
import ua.bossly.tools.translit.TransformType
import ua.bossly.tools.translit.TransformTypes
import ua.bossly.tools.translit.WordTransformation
import ua.bossly.tools.translit.data.AppDatabase
import ua.bossly.tools.translit.data.TransliterationHistory
import ua.bossly.tools.translit.data.TransliterationRepository

/**
 * Agent-callable building blocks and Jetpack AppFunctions registry for the Gemini ecosystem.
 *
 * Provides headless execution of transliteration operations, standard discovery,
 * history querying, and intent resolution for foreground UI transitions.
 */
object TranslitAppFunctions {

    /**
     * Executes headless text transliteration.
     * Callable directly by Gemini / AI Assistants without opening an Activity.
     *
     * @param context Application context.
     * @param text Ukrainian or Latin text to transform.
     * @param systemId Preferred transliteration standard name or ID (defaults to Passport 2010 if unspecified).
     * @param saveToHistory Whether to save the transliteration record into Room database history.
     */
    fun transliterateText(
        context: Context,
        text: String,
        systemId: String? = null,
        saveToHistory: Boolean = true
    ): TranslitResultResponse {
        val availableSystems = TransformTypes.types(context)
        val selectedSystem = findSystem(availableSystems, systemId) ?: availableSystems.first()

        val resultText = WordTransformation.transform(text, selectedSystem)

        if (saveToHistory) {
            saveHistoryHeadless(context, text, resultText, selectedSystem)
        }

        return TranslitResultResponse(
            originalText = text,
            transliteratedText = resultText,
            systemName = selectedSystem.name,
            systemTip = selectedSystem.tip
        )
    }

    /**
     * Concise function endpoint enabling simple invocation: transliterate("слово").
     *
     * @param context Application context.
     * @param text Text string to transform.
     */
    fun transliterate(
        context: Context,
        text: String
    ): TranslitResultResponse = transliterateText(context, text)

    /**
     * Returns all supported transliteration standards for capability discovery by Gemini agents.
     */
    fun getSupportedSystems(context: Context): List<TranslitSystemInfo> {
        return TransformTypes.types(context).mapIndexed { index, system ->
            TranslitSystemInfo(
                id = "system_$index",
                name = system.name,
                tip = system.tip
            )
        }
    }

    /**
     * Queries recent transliteration history entries.
     */
    fun getHistory(context: Context, limit: Int = 10): List<HistoryItemResponse> {
        val database = AppDatabase.getDatabase(context.applicationContext)
        val repository = TransliterationRepository(database.transliterationDao())

        return runBlocking {
            val list = repository.history.first()
            list.take(limit.coerceAtLeast(1)).map { item ->
                HistoryItemResponse(
                    id = item.id,
                    inputText = item.inputText,
                    outputText = item.outputText,
                    transformType = item.transformType,
                    timestamp = item.timestamp
                )
            }
        }
    }

    /**
     * Creates an Intent to launch the interactive App UI with pre-filled text and selected feature.
     */
    fun createOpenTransliterationIntent(
        context: Context,
        text: String? = null,
        systemId: String? = null
    ): Intent {
        val uriString = if (!text.isNullOrBlank()) {
            "uatranslit://transliterate?text=${Uri.encode(text)}"
        } else {
            "uatranslit://open?feature=${systemId ?: "transliterate"}"
        }

        return Intent(Intent.ACTION_VIEW, uriString.toUri()).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun findSystem(systems: Array<TransformType>, systemId: String?): TransformType? {
        if (systemId.isNullOrBlank()) return null
        return systems.find { system ->
            system.name.contains(systemId, ignoreCase = true) ||
                    system.tip.contains(systemId, ignoreCase = true)
        }
    }

    private fun saveHistoryHeadless(
        context: Context,
        inputText: String,
        outputText: String,
        transformType: TransformType
    ) {
        val database = AppDatabase.getDatabase(context.applicationContext)
        val repository = TransliterationRepository(database.transliterationDao())
        runBlocking {
            if (!repository.exists(inputText, outputText)) {
                repository.insert(
                    TransliterationHistory(
                        inputText = inputText,
                        outputText = outputText,
                        transformType = transformType.name
                    )
                )
            }
        }
    }
}
