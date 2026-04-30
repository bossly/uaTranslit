package ua.bossly.tools.translit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transliteration_history")
data class TransliterationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val inputText: String,
    val outputText: String,
    val transformType: String,
    val timestamp: Long = System.currentTimeMillis()
)
