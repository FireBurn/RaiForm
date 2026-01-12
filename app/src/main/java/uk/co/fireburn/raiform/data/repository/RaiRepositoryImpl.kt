package uk.co.fireburn.raiform.data.repository

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.fireburn.raiform.data.source.local.RaiFormDatabase
import uk.co.fireburn.raiform.data.source.local.dao.ClientDao
import uk.co.fireburn.raiform.data.source.local.dao.HistoryDao
import uk.co.fireburn.raiform.data.source.local.dao.SessionDao
import uk.co.fireburn.raiform.data.source.local.entity.ClientEntity
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.HistoryEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.ClientStatus
import uk.co.fireburn.raiform.domain.model.HistoryLog
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaiRepositoryImpl @Inject constructor(
    private val db: RaiFormDatabase,
    private val clientDao: ClientDao,
    private val sessionDao: SessionDao,
    private val historyDao: HistoryDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : RaiRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Clients ---

    override fun getAllClients(): Flow<List<Client>> {
        return clientDao.getAllClients().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getActiveClients(): Flow<List<Client>> {
        return clientDao.getClientsByStatus(ClientStatus.ACTIVE)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getArchivedClients(): Flow<List<Client>> {
        return clientDao.getClientsByStatus(ClientStatus.REMOVED)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getClient(clientId: String): Flow<Client?> {
        return clientDao.getClientById(clientId).map { it?.toDomain() }
    }

    override suspend fun saveClient(client: Client) {
        val entity = ClientEntity.fromDomain(client)
        // Perform manual Upsert to avoid REPLACE causing Cascade Delete of Sessions
        val updatedRows = clientDao.updateClient(entity)
        if (updatedRows == 0) {
            clientDao.insertClient(entity)
        }
    }

    override suspend fun archiveClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.REMOVED)
    }

    override suspend fun restoreClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.ACTIVE)
    }

    override suspend fun deleteClient(clientId: String) {
        clientDao.deleteClient(clientId)
    }

    // --- Sessions ---

    override fun getSessionsForClient(clientId: String): Flow<List<Session>> {
        return sessionDao.getSessionsForClient(clientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSession(sessionId: String): Flow<Session?> {
        return sessionDao.getSessionById(sessionId).map { it?.toDomain() }
    }

    override fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSession(session: Session) {
        db.withTransaction {
            sessionDao.insertSession(SessionEntity.fromDomain(session))
            sessionDao.clearLinksForSession(session.id)

            session.exercises.forEachIndexed { index, exercise ->
                val formattedName = exercise.name.trim()
                var template = sessionDao.findTemplateByName(session.clientId, formattedName)

                if (template == null) {
                    template = ExerciseTemplateEntity(
                        id = UUID.randomUUID().toString(),
                        clientId = session.clientId,
                        name = formattedName,
                        weight = exercise.weight,
                        isBodyweight = exercise.isBodyweight,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        maintainWeight = exercise.maintainWeight
                    )
                    sessionDao.insertTemplate(template)
                } else {
                    val updatedTemplate = template.copy(
                        weight = exercise.weight,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        maintainWeight = exercise.maintainWeight,
                        isBodyweight = exercise.isBodyweight
                    )
                    sessionDao.updateTemplate(updatedTemplate)
                    template = updatedTemplate
                }

                val link = SessionExerciseEntity(
                    id = if (exercise.id.length > 10) exercise.id else UUID.randomUUID().toString(),
                    sessionId = session.id,
                    templateId = template.id,
                    isDone = exercise.isDone,
                    orderIndex = index
                )
                sessionDao.insertLink(link)
            }
        }
    }

    override suspend fun updateSessionOrder(sessions: List<Session>) {
        db.withTransaction {
            sessions.forEach { saveSession(it) }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }

    // --- History ---

    override fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>> {
        return historyDao.getHistoryForClient(clientId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getAllHistoryLogs(): Flow<List<HistoryLog>> {
        return historyDao.getAllHistoryLogs().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun logHistory(log: HistoryLog) {
        historyDao.insertLog(HistoryEntity.fromDomain(log))
    }

    // --- SYNC ---

    override suspend fun sync() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) return

        val uid = currentUser.uid
        val userDocRef = firestore.collection("users").document(uid)

        try {
            // 1. Sync Clients
            val clients = clientDao.getAllClients().first()
            clients.forEach { clientEntity ->
                userDocRef.collection("clients")
                    .document(clientEntity.id)
                    .set(clientEntity, SetOptions.merge())
            }

            // 2. Sync Sessions
            val sessions = sessionDao.getAllSessions().first()
            sessions.forEach { sessionPopulated ->
                val domainSession = sessionPopulated.toDomain()
                userDocRef.collection("sessions")
                    .document(domainSession.id)
                    .set(domainSession, SetOptions.merge())
            }

            // 3. Sync History
            val history = historyDao.getAllHistoryLogs().first()
            history.forEach { historyEntity ->
                userDocRef.collection("history")
                    .document(historyEntity.id)
                    .set(historyEntity, SetOptions.merge())
            }

            // Timestamp
            userDocRef.set(mapOf("lastSync" to System.currentTimeMillis()), SetOptions.merge())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
