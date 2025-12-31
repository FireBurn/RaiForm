package uk.co.fireburn.raiform.presentation.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import javax.inject.Inject

@HiltViewModel
class ArchivedClientsViewModel @Inject constructor(
    private val repository: ClientRepository
) : ViewModel() {

    private val _archivedClients = MutableStateFlow<List<Client>>(emptyList())
    val archivedClients: StateFlow<List<Client>> = _archivedClients.asStateFlow()

    init {
        getArchivedClients()
    }

    private fun getArchivedClients() {
        viewModelScope.launch {
            repository.getArchivedClients().collect { clients ->
                _archivedClients.value = clients
            }
        }
    }

    fun restoreClient(client: Client) {
        viewModelScope.launch {
            repository.restoreClient(client.id)
        }
    }
}
