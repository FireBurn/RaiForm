package uk.co.fireburn.raiform.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.util.Locale
import javax.inject.Inject

class ManageClientUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    suspend fun createClient(name: String) {
        if (name.isBlank()) return
        val newClient = Client(name = name.toTitleCase())
        repository.saveClient(newClient)
    }

    suspend fun renameClient(client: Client, newName: String) {
        if (newName.isBlank()) return
        val updatedClient = client.copy(name = newName.toTitleCase())
        repository.saveClient(updatedClient)
    }

    suspend fun archiveClient(client: Client) {
        repository.archiveClient(client.id)
    }

    suspend fun restoreClient(client: Client) {
        repository.restoreClient(client.id)
    }

    suspend fun deleteClient(client: Client) {
        repository.deleteClient(client.id)
    }

    fun getArchivedClients(): Flow<List<Client>> {
        return repository.getArchivedClients()
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
