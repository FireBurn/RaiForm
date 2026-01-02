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

    override suspend fun deleteClient(clientId: String) {
        // 1. Delete Local
        // Room foreign keys are set to CASCADE, so sessions and history will be deleted automatically.
        clientDao.deleteClient(clientId)

        // 2. Delete Remote
        repositoryScope.launch {
            try {
                firestore.collection("clients").document(clientId).delete()
                // Note: Firestore does not automatically delete subcollections.
                // We leave them as orphans or handle via Cloud Functions for simplicity here.
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        sessionDao.deleteSession(sessionId)

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
                val clientSnapshot = firestore.collection("clients")
                    .whereNotEqualTo("status", "REMOVED")
                    .get().await()

                val clients = clientSnapshot.toObjects(Client::class.java)
                val clientEntities = clients.map { ClientEntity.fromDomain(it) }

                if (clientEntities.isNotEmpty()) {
                    clientDao.insertClients(clientEntities)
                }

                clients.forEach { client ->
                    val sessionSnapshot = firestore.collection("clients")
                        .document(client.id)
                        .collection("sessions")
                        .get().await()

                    val sessions = sessionSnapshot.documents.map { doc ->
                        doc.toObject(Session::class.java)!!.copy(clientId = client.id)
                    }
                    val sessionEntities =
                        sessions.map { SessionEntity.fromDomain(it) }

                    if (sessionEntities.isNotEmpty()) {
                        sessionDao.insertSessions(sessionEntities)
                    }

                    val historySnapshot = firestore.collection("clients")
                        .document(client.id)
                        .collection("history")
                        .limit(50)
                        .get().await()

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
