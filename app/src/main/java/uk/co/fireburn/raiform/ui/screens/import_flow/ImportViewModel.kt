package uk.co.fireburn.raiform.ui.screens.import_flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.usecase.ImportLegacyNoteUseCase
import javax.inject.Inject

data class ImportUiState(
    val rawText: String = "",
    val parsedClientName: String? = null,
    val parsedSessions: List<Session> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importLegacyNoteUseCase: ImportLegacyNoteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onRawTextChanged(text: String) {
        _uiState.update { it.copy(rawText = text, error = null) }
    }

    fun onParseClicked() {
        val text = _uiState.value.rawText
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Please paste text first.") }
            return
        }

        try {
            // Use the UseCase for preview logic
            val result = importLegacyNoteUseCase.preview(text)
            _uiState.update {
                it.copy(
                    parsedClientName = result.clientName,
                    parsedSessions = result.sessions,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Parse Failed: ${e.message}") }
        }
    }

    fun onSaveClicked() {
        val text = _uiState.value.rawText
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = importLegacyNoteUseCase(text)

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = "Save Error: ${e.message}") }
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState()
    }
}
