package ua.bossly.tools.translit.data

import kotlinx.coroutines.flow.Flow

class TransliterationRepository(private val dao: TransliterationDao) {

    val history: Flow<List<TransliterationHistory>> = dao.getAllHistory()

    suspend fun insert(history: TransliterationHistory) {
        dao.insert(history)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun exists(inputText: String, outputText: String): Boolean {
        return dao.exists(inputText, outputText)
    }
}
