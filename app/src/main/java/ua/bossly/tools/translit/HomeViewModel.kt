package ua.bossly.tools.translit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.bossly.tools.translit.data.TransliterationHistory
import ua.bossly.tools.translit.data.TransliterationRepository

class HomeViewModel(private val repository: TransliterationRepository) : ViewModel() {
    sealed class UiEvent {
        object SaveSuccess : UiEvent()
        object AlreadyExists : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    val history: StateFlow<List<TransliterationHistory>> = repository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun types(context: Context): Array<TransformType> {
        return TransformTypes.types(context = context)
    }

    fun transliterate(text: String, transformType: TransformType): String {
        val result = WordTransformation.transform(text, transformType)
        saveToHistory(text, result, transformType)
        return result
    }

    fun saveToHistory(inputText: String, outputText: String, transformType: TransformType) {
        val trimmedInput = inputText.trim()
        val trimmedOutput = outputText.trim()
        if (trimmedInput.isEmpty()) return
        viewModelScope.launch {
            if (!repository.exists(trimmedInput, trimmedOutput)) {
                repository.insert(
                    TransliterationHistory(
                        inputText = trimmedInput,
                        outputText = trimmedOutput,
                        transformType = transformType.name
                    )
                )
                _uiEvent.emit(UiEvent.SaveSuccess)
            } else {
                _uiEvent.emit(UiEvent.AlreadyExists)
            }
        }
    }

    fun deleteFromHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
