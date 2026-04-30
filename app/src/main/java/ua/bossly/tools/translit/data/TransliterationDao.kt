package ua.bossly.tools.translit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransliterationDao {
    @Query("SELECT * FROM transliteration_history ORDER BY timestamp DESC")
    fun getAllHistory(): kotlinx.coroutines.flow.Flow<List<TransliterationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: TransliterationHistory)

    @Query("DELETE FROM transliteration_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transliteration_history")
    suspend fun clearAll()

    @Query("SELECT EXISTS(SELECT 1 FROM transliteration_history WHERE inputText = :inputText AND outputText = :outputText)")
    suspend fun exists(inputText: String, outputText: String): Boolean
}
