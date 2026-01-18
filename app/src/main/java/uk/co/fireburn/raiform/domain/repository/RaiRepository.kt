package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.domain.model.BodyMeasurement
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

    // Fetch linked sessions (Required for smart import replication & linked completion)
    suspend fun getSessionsByGroup(clientId: String, groupId: String): List<Session>

    // Exercise name autocomplete
    fun getAllExerciseNames(): Flow<List<String>>

    // Helper to find previous stats
    suspend fun findExerciseStats(clientId: String, exerciseName: String): Triple<Double, Int, Int>?

    suspend fun saveSession(session: Session)

    suspend fun updateSessionOrder(sessions: List<Session>)

    suspend fun deleteSession(sessionId: String)

    // --- Exercise Definitions (Body Parts & Global Rename) ---

    /**
     * Saves or updates the body part mapping for a specific exercise name.
     */
    suspend fun saveExerciseDefinition(name: String, bodyPart: String)

    /**
     * Returns a map of Exercise Name -> Body Part.
     */
    fun getAllExerciseBodyParts(): Flow<Map<String, String>>

    /**
     * Renames an exercise globally across all templates and definitions.
     */
    suspend fun renameExerciseGlobally(oldName: String, newName: String)

    // --- Body Measurements ---

    fun getBodyMeasurements(clientId: String): Flow<List<BodyMeasurement>>

    suspend fun saveBodyMeasurement(measurement: BodyMeasurement)

    suspend fun deleteBodyMeasurement(id: String)

    // --- History & Logging ---
    fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>>

    fun getAllHistoryLogs(): Flow<List<HistoryLog>>

    suspend fun logHistory(log: HistoryLog)

    // --- Sync & Data Management ---
    suspend fun sync()
}
