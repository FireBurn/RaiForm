package uk.co.fireburn.raiform.data.repository

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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
        val updatedRows = clientDao.updateClient(entity)
        if (updatedRows == 0) {
            clientDao.insertClient(entity)
        }
    }

    override suspend fun archiveClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.REMOVED, System.currentTimeMillis())
    }

    override suspend fun restoreClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.ACTIVE, System.currentTimeMillis())
    }

    override suspend fun deleteClient(clientId: String) {
        clientDao.softDeleteClient(clientId, System.currentTimeMillis())
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

    override suspend fun getSessionsByGroup(clientId: String, groupId: String): List<Session> {
        return sessionDao.getSessionsByGroup(clientId, groupId).map { it.toDomain() }
    }

    override fun getAllExerciseNames(): Flow<List<String>> {
        return sessionDao.getAllExerciseNames()
    }

    override suspend fun findExerciseStats(
        clientId: String,
        exerciseName: String
    ): Triple<Double, Int, Int>? {
        val template = sessionDao.findTemplateByName(clientId, exerciseName.trim())
        return if (template != null) {
            Triple(template.weight, template.sets, template.reps)
        } else null
    }

    override suspend fun saveSession(session: Session) {
        db.withTransaction {
            sessionDao.insertSession(SessionEntity.fromDomain(session))

            val existingLinks = sessionDao.getLinksForSession(session.id)
            val existingLinkMap = existingLinks.associateBy { it.id }
            val currentLinks = mutableListOf<SessionExerciseEntity>()

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

                val linkId =
                    if (exercise.id.length > 10) exercise.id else UUID.randomUUID().toString()
                val link = SessionExerciseEntity(
                    id = linkId,
                    sessionId = session.id,
                    templateId = template.id,
                    isDone = exercise.isDone,
                    orderIndex = index
                )
                currentLinks.add(link)
            }

            val linksToDelete =
                existingLinks.filter { old -> currentLinks.none { new -> new.id == old.id } }
            val linksToInsert = currentLinks.filter { new -> existingLinkMap[new.id] == null }
            val linksToUpdate = currentLinks.filter { new ->
                val old = existingLinkMap[new.id]
                old != null && old != new
            }

            if (linksToDelete.isNotEmpty()) sessionDao.deleteLinks(linksToDelete)
            if (linksToInsert.isNotEmpty()) sessionDao.insertLinks(linksToInsert)
            linksToUpdate.forEach { sessionDao.updateLink(it) }
        }
    }

    override suspend fun updateSessionOrder(sessions: List<Session>) {
        db.withTransaction {
            sessions.forEach { saveSession(it) }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.softDeleteSession(sessionId, System.currentTimeMillis())
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
        val currentUser = firebaseAuth.currentUser ?: return
        val uid = currentUser.uid
        val userDocRef = firestore.collection("users").document(uid)

        try {
            val userMetaSnapshot = userDocRef.get().await()
            val lastSyncTime = userMetaSnapshot.getLong("lastSync") ?: 0L
            val currentTime = System.currentTimeMillis()

            // PUSH
            val changedClients = clientDao.getClientsForSync(lastSyncTime)
            val changedSessions = sessionDao.getSessionsForSync(lastSyncTime)
            val changedHistory = historyDao.getAllHistoryLogs().first()
                .filter { it.lastSyncTimestamp > lastSyncTime }

            val batch = firestore.batch()
            var hasChanges = false

            changedClients.forEach {
                batch.set(userDocRef.collection("clients").document(it.id), it, SetOptions.merge())
                hasChanges = true
            }

            changedSessions.forEach {
                batch.set(userDocRef.collection("sessions").document(it.id), it, SetOptions.merge())
                hasChanges = true
            }

            changedHistory.forEach {
                batch.set(userDocRef.collection("history").document(it.id), it, SetOptions.merge())
                hasChanges = true
            }

            if (hasChanges) {
                batch.commit().await()
            }

            // PULL
            val remoteClientsSnapshot = userDocRef.collection("clients")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

            val remoteSessionsSnapshot = userDocRef.collection("sessions")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

            val remoteHistorySnapshot = userDocRef.collection("history")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

            db.withTransaction {
                val remoteClients = remoteClientsSnapshot.toObjects(ClientEntity::class.java)
                if (remoteClients.isNotEmpty()) clientDao.insertClients(remoteClients)

                val remoteHistory = remoteHistorySnapshot.toObjects(HistoryEntity::class.java)
                if (remoteHistory.isNotEmpty()) historyDao.insertLogs(remoteHistory)

                for (doc in remoteSessionsSnapshot.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") ?: false
                    if (isDeleted) {
                        val entity = doc.toObject(SessionEntity::class.java)
                        if (entity != null) sessionDao.insertSession(entity)
                    } else {
                        val domainSession = doc.toObject(Session::class.java)
                        if (domainSession != null) saveSession(domainSession)
                    }
                }
            }

            userDocRef.set(mapOf("lastSync" to currentTime), SetOptions.merge())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
