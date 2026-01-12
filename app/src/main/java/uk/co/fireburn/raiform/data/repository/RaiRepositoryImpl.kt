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
     * Smart Save Logic: Uses diffing to avoid destroying links on every save.
     */
    override suspend fun saveSession(session: Session) {
        db.withTransaction {
            // 1. Update the Session Entity itself
            // converting from domain updates lastSyncTimestamp
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

    // --- SYNC (Cloud Push & Pull with Deltas) ---

    override suspend fun sync() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) return

        val uid = currentUser.uid
        val userDocRef = firestore.collection("users").document(uid)

        try {
            // 1. Get last successful sync time from Firestore
            val userMetaSnapshot = userDocRef.get().await()
            val lastSyncTime = userMetaSnapshot.getLong("lastSync") ?: 0L
            val currentTime = System.currentTimeMillis()

            // --- 2. OPTIMIZED PUSH (Local -> Remote) ---
            // Only push items modified AFTER the last sync

            val localClients = clientDao.getAllClients().first()
                .filter { it.lastSyncTimestamp > lastSyncTime }

            // Access .session property because getAllSessions returns SessionPopulated
            val localSessions = sessionDao.getAllSessions().first()
                .filter { it.session.lastSyncTimestamp > lastSyncTime }

            val localHistory = historyDao.getAllHistoryLogs().first()
                .filter { it.lastSyncTimestamp > lastSyncTime }

            val batch = firestore.batch()
            var hasChanges = false

            localClients.forEach {
                batch.set(userDocRef.collection("clients").document(it.id), it, SetOptions.merge())
                hasChanges = true
            }

            localSessions.forEach { populatedSession ->
                // Flatten Session Structure for Cloud
                val domainSession = populatedSession.toDomain()
                batch.set(
                    userDocRef.collection("sessions").document(domainSession.id),
                    domainSession,
                    SetOptions.merge()
                )
                hasChanges = true
            }

            localHistory.forEach {
                batch.set(userDocRef.collection("history").document(it.id), it, SetOptions.merge())
                hasChanges = true
            }

            if (hasChanges) {
                batch.commit().await()
            }

            // --- 3. OPTIMIZED PULL (Remote -> Local) ---
            // Only fetch remote items modified AFTER the last sync

            val remoteClientsSnapshot = userDocRef.collection("clients")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

            val remoteSessionsSnapshot = userDocRef.collection("sessions")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

            val remoteHistorySnapshot = userDocRef.collection("history")
                .whereGreaterThan("lastSyncTimestamp", lastSyncTime).get().await()

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
                // Reconstruct relational structure via saveSession
                for (doc in remoteSessionsSnapshot.documents) {
                    val remoteSession = doc.toObject(Session::class.java)
                    if (remoteSession != null) {
                        saveSession(remoteSession)
                    }
                }
            }

            // 4. Update timestamp for next sync
            userDocRef.set(mapOf("lastSync" to currentTime), SetOptions.merge())

        } catch (e: Exception) {
            e.printStackTrace()
            // In production, report this to Crashlytics
        }
    }

}
