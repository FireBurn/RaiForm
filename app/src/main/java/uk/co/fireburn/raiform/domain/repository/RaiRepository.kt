package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.HistoryLog
import uk.co.fireburn.raiform.domain.model.Session

/**
 * Main Repository Interface.
 * Acts as the single source of truth for data access, abstracting away
 * the specific data sources (Room/Local vs Firestore/Remote).
 */
interface RaiRepository {

    // --- Clients ---
    fun getAllClients(): Flow<List<Client>>

    fun getActiveClients(): Flow<List<Client>>

    fun getArchivedClients(): Flow<List<Client>>

    fun getClient(clientId: String): Flow<Client?>

    suspend fun saveClient(client: Client)

    suspend fun archiveClient(clientId: String)

    suspend fun restoreClient(clientId: String)

    // --- Sessions ---
    fun getSessionsForClient(clientId: String): Flow<List<Session>>

    fun getSession(sessionId: String): Flow<Session?>

    /**
     * Used for the Global Scheduler to see all slots across all clients.
     */
    fun getAllSessions(): Flow<List<Session>>

    suspend fun saveSession(session: Session)

    suspend fun updateSessionOrder(sessions: List<Session>)

    suspend fun deleteSession(sessionId: String)

    // --- History & Logging ---
    fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>>

    // Get all history logs (for export)
    fun getAllHistoryLogs(): Flow<List<HistoryLog>>

    suspend fun logHistory(log: HistoryLog)

    // --- Sync & Data Management ---
    /**
     * Triggers a synchronization between Local DB (Room) and Remote (Firestore).
     * Usually called by WorkManager, but can be forced by UI.
     */
    suspend fun sync()
}
