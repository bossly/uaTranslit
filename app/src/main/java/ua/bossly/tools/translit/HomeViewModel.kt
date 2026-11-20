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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import ua.bossly.tools.translit.data.TransliterationHistory
import ua.bossly.tools.translit.data.TransliterationRepository

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: TransliterationRepository) : ViewModel() {
    sealed class UiEvent {
        object SaveSuccess : UiEvent()
        object AlreadyExists : UiEvent()
    }

    private data class TransformRequest(
        val text: String,
        val type: TransformType?,
        val direction: TranslitDirection,
        val autocorrect: Boolean
    )

    @Volatile
    private var appContext: Context? = null

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _typesState = MutableStateFlow<List<TransformType>>(emptyList())
    val typesState: StateFlow<List<TransformType>> = _typesState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<TransformType?>(null)
    private val _manualDirection = MutableStateFlow<TranslitDirection?>(null)
    private val _autocorrectEnabled = MutableStateFlow(true)
    val autocorrectEnabled: StateFlow<Boolean> = _autocorrectEnabled.asStateFlow()

    val effectiveDirection: StateFlow<TranslitDirection> = combine(
        _inputText,
        _selectedType,
        _manualDirection
    ) { text, type, manual ->
        manual ?: WordTransformation.detectDirection(text, type)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TranslitDirection.CYRILLIC_TO_LATIN
    )

    val outputText: StateFlow<String> = combine(
        _inputText,
        _selectedType,
        effectiveDirection,
        _autocorrectEnabled
    ) { text, type, direction, autocorrect ->
        TransformRequest(text, type, direction, autocorrect)
    }
        .transformLatest { req ->
            if (req.text.isEmpty() || req.type == null) {
                emit("")
            } else {
                val baseResult = WordTransformation.transform(
                    text = req.text,
                    transform = req.type,
                    direction = req.direction,
                    autocorrect = req.autocorrect,
                    context = appContext
                )
                emit(baseResult)

                val ctx = appContext
                if (ctx != null &&
                    req.direction == TranslitDirection.LATIN_TO_CYRILLIC &&
                    req.autocorrect &&
                    req.type.snapSeparator() != " "
                ) {
                    val refined = SystemSpellCheckerHelper.refineUnknownWords(ctx, baseResult)
                    if (refined != baseResult) {
                        emit(refined)
                    }
                }
            }
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
        appContext = context.applicationContext
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
            UkrainianDictionary.ensureLoaded(context.applicationContext)
        }
    }

    fun updateInput(text: String) {
        if (text.isEmpty()) {
            _manualDirection.value = null
        }
        _inputText.value = text
    }

    fun updateSelectedType(type: TransformType) {
        _selectedType.value = type
    }

    fun setAutocorrectEnabled(enabled: Boolean) {
        _autocorrectEnabled.value = enabled
    }

    fun resetManualDirection() {
        _manualDirection.value = null
    }

    /**
     * Flips the active conversion direction and moves the current output text into the input field,
     * locking the manual override until the input field is cleared.
     *
     * @return The new input text after swapping.
     */
    fun swapDirection(): String {
        val currentDir = effectiveDirection.value
        val newDir = if (currentDir == TranslitDirection.CYRILLIC_TO_LATIN) {
            TranslitDirection.LATIN_TO_CYRILLIC
        } else {
            TranslitDirection.CYRILLIC_TO_LATIN
        }
        val currentOut = outputText.value
        _manualDirection.value = newDir
        if (currentOut.isNotEmpty()) {
            _inputText.value = currentOut
            return currentOut
        }
        return _inputText.value
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
