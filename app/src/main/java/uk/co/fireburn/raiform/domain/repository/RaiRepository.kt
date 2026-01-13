package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.HistoryLog
import uk.co.fireburn.raiform.domain.model.Session

interface RaiRepository {

    // --- Clients ---
    fun getAllClients(): Flow<List<Client>>

    fun getActiveClients(): Flow<List<Client>>

    fun getArchivedClients(): Flow<List<Client>>

    fun getClient(clientId: String): Flow<Client?>

    suspend fun saveClient(client: Client)

    suspend fun archiveClient(clientId: String)

    suspend fun restoreClient(clientId: String)

    suspend fun deleteClient(clientId: String)

    // --- Sessions ---
    fun getSessionsForClient(clientId: String): Flow<List<Session>>

    fun getSession(sessionId: String): Flow<Session?>

    fun getAllSessions(): Flow<List<Session>>

    // Fetch linked sessions (Required for smart import replication)
    suspend fun getSessionsByGroup(clientId: String, groupId: String): List<Session>

    // Exercise name autocomplete
    fun getAllExerciseNames(): Flow<List<String>>

    // Helper to find previous stats
    suspend fun findExerciseStats(clientId: String, exerciseName: String): Triple<Double, Int, Int>?

    suspend fun saveSession(session: Session)

    suspend fun updateSessionOrder(sessions: List<Session>)

    suspend fun deleteSession(sessionId: String)

    // --- History & Logging ---
    fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>>

    fun getAllHistoryLogs(): Flow<List<HistoryLog>>

    suspend fun logHistory(log: HistoryLog)

    // --- Sync & Data Management ---
    suspend fun sync()
}
