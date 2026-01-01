package uk.co.fireburn.raiform.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.ClientStatus
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import uk.co.fireburn.raiform.widget.RaiFormWidgetUpdater
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val widgetUpdater: RaiFormWidgetUpdater
) : ClientRepository {

    private val clientsCollection = firestore.collection("clients")

    override fun getClients(): Flow<List<Client>> = callbackFlow {
        val listener = clientsCollection
            .whereNotEqualTo("status", "REMOVED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val clients = snapshot?.documents?.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: "Unknown"
                    val statusStr = doc.getString("status") ?: "ACTIVE"
                    val status = try {
                        ClientStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        ClientStatus.ACTIVE
                    }
                    val dateAdded = doc.getLong("dateAdded") ?: System.currentTimeMillis()
                    val notes = doc.getString("notes") ?: ""
                    val resetDay = doc.getLong("weeklyResetDay")?.toInt() ?: 7

                    Client(id, name, status, notes, dateAdded, resetDay)
                } ?: emptyList()

                trySend(clients)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getClientById(clientId: String): Client? {
        val snapshot = clientsCollection.document(clientId).get().await()
        if (!snapshot.exists()) return null

        val name = snapshot.getString("name") ?: return null
        val statusStr = snapshot.getString("status") ?: "ACTIVE"
        val status = try {
            ClientStatus.valueOf(statusStr)
        } catch (e: Exception) {
            ClientStatus.ACTIVE
        }

        return Client(
            id = snapshot.id,
            name = name,
            status = status,
            notes = snapshot.getString("notes") ?: "",
            dateAdded = snapshot.getLong("dateAdded") ?: 0L,
            weeklyResetDay = snapshot.getLong("weeklyResetDay")?.toInt() ?: 7
        )
    }

    override suspend fun saveClient(client: Client) {
        clientsCollection.document(client.id).set(client).await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun archiveClient(clientId: String) {
        clientsCollection.document(clientId)
            .update("status", ClientStatus.REMOVED.name)
            .await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun saveClientWithSessions(client: Client, sessions: List<Session>) {
        val batch = firestore.batch()
        val clientRef = clientsCollection.document(client.id)
        batch.set(clientRef, client)

        val sessionsCollection = clientRef.collection("sessions")
        sessions.forEach { session ->
            val sessionRef = sessionsCollection.document(session.id)
            batch.set(sessionRef, session)
        }
        batch.commit().await()
        widgetUpdater.triggerUpdate()
    }

    override fun getArchivedClients(): Flow<List<Client>> = callbackFlow {
        val listener = clientsCollection
            .whereEqualTo("status", "REMOVED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val clients = snapshot?.documents?.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: "Unknown"
                    val status = ClientStatus.REMOVED
                    val dateAdded = doc.getLong("dateAdded") ?: 0L
                    val notes = doc.getString("notes") ?: ""
                    val resetDay = doc.getLong("weeklyResetDay")?.toInt() ?: 7

                    Client(id, name, status, notes, dateAdded, resetDay)
                } ?: emptyList()
                trySend(clients)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun restoreClient(clientId: String) {
        clientsCollection.document(clientId)
            .update("status", ClientStatus.ACTIVE.name)
            .await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun updateClientSessionsOrder(clientId: String, sessions: List<Session>) {
        val batch = firestore.batch()
        val sessionsRef = clientsCollection.document(clientId).collection("sessions")

        sessions.forEach { session ->
            batch.set(sessionsRef.document(session.id), session)
        }
        batch.commit().await()
        widgetUpdater.triggerUpdate()
    }

    override fun getSessionsForClient(clientId: String): Flow<List<Session>> = callbackFlow {
        val listener = clientsCollection.document(clientId)
            .collection("sessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Session::class.java)
                } ?: emptyList()

                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateSession(clientId: String, session: Session) {
        clientsCollection.document(clientId)
            .collection("sessions")
            .document(session.id)
            .set(session)
            .await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun deleteSession(clientId: String, sessionId: String) {
        clientsCollection.document(clientId)
            .collection("sessions")
            .document(sessionId)
            .delete()
            .await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun getAllSessionsFromAllClients(): List<Session> {
        val clientsSnapshot = clientsCollection
            .whereNotEqualTo("status", "REMOVED")
            .get().await()

        val allSessions = mutableListOf<Session>()

        for (doc in clientsSnapshot.documents) {
            val sessionsSnapshot = doc.reference.collection("sessions").get().await()
            val sessions = sessionsSnapshot.toObjects(Session::class.java)
            allSessions.addAll(sessions)
        }
        return allSessions
    }

    // --- UPDATED: Robust Fetch for Widget ---
    override suspend fun getClientsAndSessions(): Map<Client, List<Session>> = coroutineScope {
        // 1. Fetch Clients (Try Network -> Fallback Cache)
        val clientsSnapshot = try {
            clientsCollection
                .whereNotEqualTo("status", "REMOVED")
                .get(Source.DEFAULT)
                .await()
        } catch (e: Exception) {
            clientsCollection
                .whereNotEqualTo("status", "REMOVED")
                .get(Source.CACHE)
                .await()
        }

        val clients = clientsSnapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: "Unknown"
            val status = try {
                ClientStatus.valueOf(doc.getString("status") ?: "ACTIVE")
            } catch (e: Exception) {
                ClientStatus.ACTIVE
            }
            val resetDay = doc.getLong("weeklyResetDay")?.toInt() ?: 7
            Client(
                id = doc.id,
                name = name,
                status = status,
                weeklyResetDay = resetDay
            )
        }

        // 2. Parallel Fetch Sessions (Try Network -> Fallback Cache)
        val deferredResults = clients.map { client ->
            async {
                val sessions = try {
                    clientsCollection.document(client.id)
                        .collection("sessions")
                        .get(Source.DEFAULT)
                        .await()
                } catch (e: Exception) {
                    clientsCollection.document(client.id)
                        .collection("sessions")
                        .get(Source.CACHE)
                        .await()
                }.toObjects(Session::class.java)
                client to sessions
            }
        }

        deferredResults.awaitAll().toMap()
    }

    override suspend fun logSessionHistory(clientId: String, sessions: List<Session>, date: Long) {
        val batch = firestore.batch()
        val historyCollection = clientsCollection.document(clientId).collection("history")

        sessions.forEach { session ->
            if (session.exercises.any { it.isDone }) {
                val historyId = "${session.id}_$date"
                val historyEntry = session.copy(lastResetTimestamp = date)
                batch.set(historyCollection.document(historyId), historyEntry)
            }
        }
        batch.commit().await()
    }

    override suspend fun getClientHistory(clientId: String): List<Session> {
        return clientsCollection.document(clientId)
            .collection("history")
            .orderBy("lastResetTimestamp", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(Session::class.java)
    }
}
