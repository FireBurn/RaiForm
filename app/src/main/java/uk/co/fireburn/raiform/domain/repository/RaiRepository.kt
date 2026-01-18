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

    suspend fun getSessionsByGroup(clientId: String, groupId: String): List<Session>

    fun getAllExerciseNames(): Flow<List<String>>

    suspend fun findExerciseStats(clientId: String, exerciseName: String): Triple<Double, Int, Int>?

    suspend fun saveSession(session: Session)

    suspend fun updateSessionOrder(sessions: List<Session>)

    suspend fun deleteSession(sessionId: String)

    // Cleanup methods for client lifecycle
    suspend fun unscheduleSessionsForClient(clientId: String)
    suspend fun softDeleteSessionsForClient(clientId: String)

    // --- Exercise Definitions ---
    suspend fun saveExerciseDefinition(name: String, bodyPart: String)

    fun getAllExerciseBodyParts(): Flow<Map<String, String>>

    suspend fun renameExerciseGlobally(oldName: String, newName: String)

    // --- Body Measurements ---
    fun getBodyMeasurements(clientId: String): Flow<List<BodyMeasurement>>

    suspend fun saveBodyMeasurement(measurement: BodyMeasurement)

    suspend fun deleteBodyMeasurement(id: String)

    // --- History & Logging ---
    fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>>

    fun getAllHistoryLogs(): Flow<List<HistoryLog>>

    suspend fun logHistory(log: HistoryLog)

    // --- Sync ---
    suspend fun sync()
}
