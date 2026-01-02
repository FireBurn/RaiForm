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
    val isSaved: Boolean = false // Trigger navigation back on true
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

        // We run a "dry run" of the parsing logic here to preview.
        // Since the UseCase saves to DB immediately, for the preview UI we might need to expose
        // the Parser directly or have a "Preview" UseCase.
        // However, sticking to the requested architecture and previous implementation logic:
        // The previous VM used `LegacyParser` directly.
        // To be clean, we should probably use the Parser utility here for preview,
        // and the UseCase for saving.
        // *Correction*: To keep Domain pure, the Parser should ideally be internal or injected.
        // For this specific UI flow (Preview -> Save), let's use the Parser utility directly here
        // as strictly presentation logic (formatting text for display),
        // then call the UseCase to commit.

        try {
            // Using the util class directly as it's a pure function
            val result = uk.co.fireburn.raiform.util.LegacyParser.parseLegacyNote(text)
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

            // The UseCase re-parses and saves.
            // This ensures the source of truth for "Saving" logic remains in the domain layer.
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
