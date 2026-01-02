package uk.co.fireburn.raiform.ui.screens.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.usecase.ManageClientUseCase
import javax.inject.Inject

@HiltViewModel
class ArchivedClientsViewModel @Inject constructor(
    private val manageClientUseCase: ManageClientUseCase
) : ViewModel() {

    val archivedClients: StateFlow<List<Client>> = manageClientUseCase.getArchivedClients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun restoreClient(client: Client) {
        viewModelScope.launch {
            manageClientUseCase.restoreClient(client)
        }
    }
}
