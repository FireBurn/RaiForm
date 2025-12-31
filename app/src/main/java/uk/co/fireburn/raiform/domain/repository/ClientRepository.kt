package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session

interface ClientRepository {

    /**
     * strict Flow observation for real-time dashboard updates.
     */
    fun getClients(): Flow<List<Client>>

    /**
     * Get a single client's details.
     */
    suspend fun getClientById(clientId: String): Client?

    /**
     * Saves a new client or updates an existing one.
     */
    suspend fun saveClient(client: Client)

    /**
     * Soft deletes a client (sets status to REMOVED).
     */
    suspend fun archiveClient(clientId: String)

    fun getArchivedClients(): Flow<List<Client>>

    suspend fun restoreClient(clientId: String)

    // Save the full list order (for drag and drop)
    suspend fun updateClientSessionsOrder(clientId: String, sessions: List<Session>)

    /**
     * Saves a new client AND their schedule in one go.
     */
    suspend fun saveClientWithSessions(client: Client, sessions: List<Session>)

    /**
     * Get sessions for a specific client (e.g., for the Active Session screen).
     */
    fun getSessionsForClient(clientId: String): Flow<List<Session>>

    /**
     * Updates a single session (e.g., ticking off exercises).
     */
    suspend fun updateSession(clientId: String, session: Session)

    /**
     * Deletes a single session
     */
    suspend fun deleteSession(clientId: String, sessionId: String)

    // Needed for the Main Scheduler to check conflicts
    suspend fun getAllSessionsFromAllClients(): List<Session>
}
