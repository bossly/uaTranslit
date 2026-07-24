package ua.bossly.tools.translit.appfunctions

/**
 * Data model representing the structured result of a transliteration request.
 * Formatted for consumption by AI agents (e.g. Google Gemini).
 */
data class TranslitResultResponse(
    val originalText: String,
    val transliteratedText: String,
    val systemName: String,
    val systemTip: String
)

/**
 * Data model representing a supported transliteration standard in the application.
 */
data class TranslitSystemInfo(
    val id: String,
    val name: String,
    val tip: String
)

/**
 * Data model representing a history item retrieved by an agent query.
 */
data class HistoryItemResponse(
    val id: Long,
    val inputText: String,
    val outputText: String,
    val transformType: String,
    val timestamp: Long
)
