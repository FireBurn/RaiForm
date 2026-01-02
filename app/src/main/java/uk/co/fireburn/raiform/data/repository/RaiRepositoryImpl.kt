package uk.co.fireburn.raiform.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import uk.co.fireburn.raiform.data.source.local.dao.ClientDao
import uk.co.fireburn.raiform.data.source.local.dao.HistoryDao
import uk.co.fireburn.raiform.data.source.local.dao.SessionDao
import uk.co.fireburn.raiform.data.source.local.entity.ClientEntity
import uk.co.fireburn.raiform.data.source.local.entity.HistoryEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.ClientStatus
import uk.co.fireburn.raiform.domain.model.HistoryLog
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaiRepositoryImpl @Inject constructor(
    private val clientDao: ClientDao,
    private val sessionDao: SessionDao,
    private val historyDao: HistoryDao,
    private val firestore: FirebaseFirestore
) : RaiRepository {

    // Scope for "Fire and Forget" remote writes
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Clients ---

    override fun getAllClients(): Flow<List<Client>> {
        return clientDao.getAllClients().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveClients(): Flow<List<Client>> {
        return clientDao.getClientsByStatus(ClientStatus.ACTIVE).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getArchivedClients(): Flow<List<Client>> {
        return clientDao.getClientsByStatus(ClientStatus.REMOVED).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getClient(clientId: String): Flow<Client?> {
        return clientDao.getClientById(clientId).map { it?.toDomain() }
    }

    override suspend fun saveClient(client: Client) {
        // 1. Update Local (Optimistic)
        clientDao.insertClient(ClientEntity.fromDomain(client))

        // 2. Update Remote (Async)
        repositoryScope.launch {
            try {
                firestore.collection("clients").document(client.id).set(client).await()
            } catch (e: Exception) {
                // TODO: Handle sync failure (Queue in WorkManager)
                e.printStackTrace()
            }
        }
    }

    override suspend fun archiveClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.REMOVED)
        repositoryScope.launch {
            firestore.collection("clients").document(clientId)
                .update("status", ClientStatus.REMOVED.name)
        }
    }

    override suspend fun restoreClient(clientId: String) {
        clientDao.updateClientStatus(clientId, ClientStatus.ACTIVE)
        repositoryScope.launch {
            firestore.collection("clients").document(clientId)
                .update("status", ClientStatus.ACTIVE.name)
        }
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
        sessionDao.insertSession(SessionEntity.fromDomain(session))

        repositoryScope.launch {
            try {
                firestore.collection("clients").document(session.clientId)
                    .collection("sessions").document(session.id)
                    .set(session).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun updateSessionOrder(sessions: List<Session>) {
        val entities = sessions.map { SessionEntity.fromDomain(it) }
        sessionDao.insertSessions(entities)

        repositoryScope.launch {
            val batch = firestore.batch()
            sessions.forEach { session ->
                val ref = firestore.collection("clients")
                    .document(session.clientId)
                    .collection("sessions").document(session.id)
                batch.set(ref, session)
            }
            batch.commit()
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        // We need clientId to delete from remote effectively, strictly speaking.
        // For now, we assume we find it or the architecture passes it.
        // In the interface, we only asked for sessionId.
        // To fix this in a real app, deleteSession should take (clientId, sessionId).
        // For now, we will delete local and try to delete remote if possible.

        sessionDao.deleteSession(sessionId)

        // Note: Without clientId, finding the document path in Firestore subcollections is hard
        // unless we use a CollectionGroup query to find the parent.
        // For this artifact, we'll assume the caller updates the list via 'updateSessionOrder' mostly,
        // or we accept that 'deleteSession' in interface needs clientId.
        // *Correction*: I will use a Collection Group query to find and delete strictly for this demo.
        repositoryScope.launch {
            val snapshot = firestore.collectionGroup("sessions")
                .whereEqualTo("id", sessionId).get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete()
            }
        }
    }

    // --- History ---

    override fun getHistoryForClient(clientId: String): Flow<List<HistoryLog>> {
        return historyDao.getHistoryForClient(clientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // New method to get ALL history logs (needed for ExportDataUseCase)
    override fun getAllHistoryLogs(): Flow<List<HistoryLog>> {
        return historyDao.getAllHistoryLogs().map { entities ->
            entities.map { it.toDomain() }
        }
    }


    override suspend fun logHistory(log: HistoryLog) {
        historyDao.insertLog(HistoryEntity.fromDomain(log))

        repositoryScope.launch {
            firestore.collection("clients").document(log.clientId)
                .collection("history").document(log.id)
                .set(log)
        }
    }

    // --- Sync ---

    override suspend fun sync() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Clients
                val clientSnapshot = firestore.collection("clients")
                    .whereNotEqualTo("status", "REMOVED")
                    .get().await()

                val clients = clientSnapshot.toObjects(Client::class.java)
                val clientEntities = clients.map { ClientEntity.fromDomain(it) }

                // Insert/Update Local
                if (clientEntities.isNotEmpty()) {
                    clientDao.insertClients(clientEntities)
                }

                // 2. Fetch Sessions for each Client (Parallel-ish)
                // In a production app, we might use a CollectionGroup query for "sessions"
                // but we need to map them to client IDs.
                clients.forEach { client ->
                    val sessionSnapshot = firestore.collection("clients")
                        .document(client.id)
                        .collection("sessions")
                        .get().await()

                    // Need to explicitly set clientId for each session when converting from Firestore object
                    // because Firestore doesn't implicitly put the parent document ID into subcollection documents
                    val sessions = sessionSnapshot.documents.map { doc ->
                        doc.toObject(Session::class.java)!!.copy(clientId = client.id)
                    }
                    val sessionEntities =
                        sessions.map { SessionEntity.fromDomain(it) }

                    if (sessionEntities.isNotEmpty()) {
                        sessionDao.insertSessions(sessionEntities)
                    }

                    // 3. Fetch History (Optional/Lazy? Fetch mostly recent?)
                    // For full sync:
                    val historySnapshot = firestore.collection("clients")
                        .document(client.id)
                        .collection("history")
                        .limit(50) // Limit to recent history for speed
                        .get().await()

                    // Need to explicitly set clientId for each history log
                    val history = historySnapshot.documents.map { doc ->
                        doc.toObject(HistoryLog::class.java)!!.copy(clientId = client.id)
                    }
                    val historyEntities = history.map { HistoryEntity.fromDomain(it) }

                    if (historyEntities.isNotEmpty()) {
                        historyDao.insertLogs(historyEntities)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
