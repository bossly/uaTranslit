package ua.bossly.tools.translit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.bossly.tools.translit.data.TransliterationHistory
import ua.bossly.tools.translit.data.TransliterationRepository

import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: TransliterationRepository) : ViewModel() {
    sealed class UiEvent {
        object SaveSuccess : UiEvent()
        object AlreadyExists : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _typesState = MutableStateFlow<List<TransformType>>(emptyList())
    val typesState: StateFlow<List<TransformType>> = _typesState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<TransformType?>(null)

    val outputText: StateFlow<String> = combine(_inputText, _selectedType) { text, type ->
        text to type
    }
        .mapLatest { (text, type) ->
            if (text.isEmpty() || type == null) "" else WordTransformation.transform(text, type)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val history: StateFlow<List<TransliterationHistory>?> = repository.history
        .map<List<TransliterationHistory>, List<TransliterationHistory>?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun loadTypesAsync(context: Context, initialTypeName: String = "") {
        if (_typesState.value.isNotEmpty()) {
            if (_selectedType.value == null) {
                _selectedType.value = _typesState.value.find { it.name == initialTypeName }
                    ?: _typesState.value.firstOrNull()
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = TransformTypes.types(context).toList()
            _typesState.value = loaded
            if (_selectedType.value == null && loaded.isNotEmpty()) {
                _selectedType.value = loaded.find { it.name == initialTypeName } ?: loaded.first()
            }
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun updateSelectedType(type: TransformType) {
        _selectedType.value = type
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
