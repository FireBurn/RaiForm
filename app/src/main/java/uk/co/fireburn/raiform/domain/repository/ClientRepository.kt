package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session

interface ClientRepository {

    fun getClients(): Flow<List<Client>>
    suspend fun getClientById(clientId: String): Client?
    suspend fun saveClient(client: Client)
    suspend fun archiveClient(clientId: String)
    fun getArchivedClients(): Flow<List<Client>>
    suspend fun restoreClient(clientId: String)
    suspend fun updateClientSessionsOrder(clientId: String, sessions: List<Session>)
    suspend fun saveClientWithSessions(client: Client, sessions: List<Session>)
    fun getSessionsForClient(clientId: String): Flow<List<Session>>
    suspend fun updateSession(clientId: String, session: Session)
    suspend fun deleteSession(clientId: String, sessionId: String)
    suspend fun getAllSessionsFromAllClients(): List<Session>

    // NEW: Efficient one-shot fetch for Widget
    suspend fun getClientsAndSessions(): Map<Client, List<Session>>
}
