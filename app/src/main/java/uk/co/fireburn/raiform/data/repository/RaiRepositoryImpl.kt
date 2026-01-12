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

    /**
     * Updated Save Logic: Uses diffing to avoid destroying links on every save.
     */
    override suspend fun saveSession(session: Session) {
        db.withTransaction {
            // 1. Update the Session Entity itself
            sessionDao.insertSession(SessionEntity.fromDomain(session))

            // 2. Fetch existing links from DB to compare against current session state
            val existingLinks = sessionDao.getLinksForSession(session.id)
            val existingLinkMap = existingLinks.associateBy { it.id }

            val currentLinks = mutableListOf<SessionExerciseEntity>()

            session.exercises.forEachIndexed { index, exercise ->
                val formattedName = exercise.name.trim()

                // 3. Find or Create the Exercise Template (Global Logic)
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
                    // Update global template to reflect latest stats
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

                // 4. Create the Link Object
                // If exercise.id is temporary/short (from UI creation), generate a UUID.
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

            // 5. Calculate Diffs
            val linksToDelete =
                existingLinks.filter { old -> currentLinks.none { new -> new.id == old.id } }
            val linksToInsert = currentLinks.filter { new -> existingLinkMap[new.id] == null }
            val linksToUpdate = currentLinks.filter { new ->
                val old = existingLinkMap[new.id]
                // Only update if data actually changed to reduce write churn
                old != null && old != new
            }

            // 6. Apply Changes
            if (linksToDelete.isNotEmpty()) {
                sessionDao.deleteLinks(linksToDelete)
            }
            if (linksToInsert.isNotEmpty()) {
                sessionDao.insertLinks(linksToInsert)
            }
            linksToUpdate.forEach {
                sessionDao.updateLink(it)
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

    // --- SYNC (Cloud Push & Pull) ---

    override suspend fun sync() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) return

        val uid = currentUser.uid
        val userDocRef = firestore.collection("users").document(uid)

        try {
            // --- 1. PUSH (Local -> Remote) ---
            val localClients = clientDao.getAllClients().first()
            val localSessions = sessionDao.getAllSessions().first()
            val localHistory = historyDao.getAllHistoryLogs().first()

            val batch = firestore.batch()

            // Push Clients
            localClients.forEach {
                batch.set(userDocRef.collection("clients").document(it.id), it, SetOptions.merge())
            }

            // Push Sessions
            // We convert to Domain first to ensure we send the full nested structure (exercises list)
            // This is critical for NoSQL storage so we don't have to join 3 tables on the server.
            localSessions.forEach { populatedSession ->
                val domainSession = populatedSession.toDomain()
                batch.set(
                    userDocRef.collection("sessions").document(domainSession.id),
                    domainSession,
                    SetOptions.merge()
                )
            }

            // Push History
            localHistory.forEach {
                batch.set(userDocRef.collection("history").document(it.id), it, SetOptions.merge())
            }

            // Commit Push
            batch.commit().await()

            // --- 2. PULL (Remote -> Local) ---
            val remoteClientsSnapshot = userDocRef.collection("clients").get().await()
            val remoteSessionsSnapshot = userDocRef.collection("sessions").get().await()
            val remoteHistorySnapshot = userDocRef.collection("history").get().await()

            db.withTransaction {
                // A. Sync Clients
                val remoteClients = remoteClientsSnapshot.toObjects(ClientEntity::class.java)
                if (remoteClients.isNotEmpty()) {
                    clientDao.insertClients(remoteClients)
                }

                // B. Sync History
                val remoteHistory = remoteHistorySnapshot.toObjects(HistoryEntity::class.java)
                if (remoteHistory.isNotEmpty()) {
                    historyDao.insertLogs(remoteHistory)
                }

                // C. Sync Sessions
                // Iterate through remote documents, convert to Domain, then save locally.
                // Using saveSession() ensures the relational tables (Templates/Links) are reconstructed correctly.
                for (doc in remoteSessionsSnapshot.documents) {
                    val remoteSession = doc.toObject(Session::class.java)
                    if (remoteSession != null) {
                        saveSession(remoteSession)
                    }
                }
            }

            // Update timestamp
            userDocRef.set(mapOf("lastSync" to System.currentTimeMillis()), SetOptions.merge())

        } catch (e: Exception) {
            e.printStackTrace()
            // In a real application, you might inject an ErrorHandler or EventBus here
            // to notify the UI or analytics about sync failures.
        }
    }
}
