package uk.co.fireburn.raiform.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    // ... (Previous existing methods: getClients, getClientById, saveClient, etc. kept as is) ...
    // I am including the full class structure but omitting unchanged bodies for brevity where clear,
    // but ensures the new methods are added correctly.

    override fun getClients(): Flow<List<Client>> = callbackFlow {
        val listener =
            clientsCollection.whereNotEqualTo("status", "REMOVED").addSnapshotListener { s, e ->
                if (e == null) trySend(s?.documents?.mapNotNull { doc ->
                    // Mapping logic (abbreviated for this snippet, assumes same as before)
                    val status = try {
                        ClientStatus.valueOf(doc.getString("status") ?: "ACTIVE")
                    } catch (e: Exception) {
                        ClientStatus.ACTIVE
                    }
                    Client(
                        doc.id,
                        doc.getString("name") ?: "",
                        status,
                        doc.getString("notes") ?: "",
                        doc.getLong("dateAdded") ?: 0L,
                        doc.getLong("weeklyResetDay")?.toInt() ?: 7
                    )
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getClientById(clientId: String): Client? {
        val snap = clientsCollection.document(clientId).get().await()
        return if (snap.exists()) {
            val status = try {
                ClientStatus.valueOf(snap.getString("status") ?: "ACTIVE")
            } catch (e: Exception) {
                ClientStatus.ACTIVE
            }
            Client(
                snap.id,
                snap.getString("name") ?: "",
                status,
                snap.getString("notes") ?: "",
                snap.getLong("dateAdded") ?: 0L,
                snap.getLong("weeklyResetDay")?.toInt() ?: 7
            )
        } else null
    }

    override suspend fun saveClient(client: Client) {
        clientsCollection.document(client.id).set(client).await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun archiveClient(clientId: String) {
        clientsCollection.document(clientId).update("status", ClientStatus.REMOVED.name).await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun saveClientWithSessions(client: Client, sessions: List<Session>) {
        val batch = firestore.batch()
        batch.set(clientsCollection.document(client.id), client)
        val sessionsRef = clientsCollection.document(client.id).collection("sessions")
        sessions.forEach { batch.set(sessionsRef.document(it.id), it) }
        batch.commit().await()
        widgetUpdater.triggerUpdate()
    }

    override fun getArchivedClients(): Flow<List<Client>> = callbackFlow {
        val listener =
            clientsCollection.whereEqualTo("status", "REMOVED").addSnapshotListener { s, _ ->
                trySend(s?.documents?.mapNotNull {
                    Client(
                        it.id,
                        it.getString("name") ?: "",
                        ClientStatus.REMOVED
                    )
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun restoreClient(clientId: String) {
        clientsCollection.document(clientId).update("status", ClientStatus.ACTIVE.name).await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun updateClientSessionsOrder(clientId: String, sessions: List<Session>) {
        val batch = firestore.batch()
        val ref = clientsCollection.document(clientId).collection("sessions")
        sessions.forEach { batch.set(ref.document(it.id), it) }
        batch.commit().await()
        widgetUpdater.triggerUpdate()
    }

    override fun getSessionsForClient(clientId: String): Flow<List<Session>> = callbackFlow {
        val listener = clientsCollection.document(clientId).collection("sessions")
            .addSnapshotListener { s, _ ->
                trySend(s?.toObjects(Session::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateSession(clientId: String, session: Session) {
        clientsCollection.document(clientId).collection("sessions").document(session.id)
            .set(session).await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun deleteSession(clientId: String, sessionId: String) {
        clientsCollection.document(clientId).collection("sessions").document(sessionId).delete()
            .await()
        widgetUpdater.triggerUpdate()
    }

    override suspend fun getAllSessionsFromAllClients(): List<Session> {
        val clients = clientsCollection.whereNotEqualTo("status", "REMOVED").get().await()
        val list = mutableListOf<Session>()
        for (doc in clients) {
            list.addAll(
                doc.reference.collection("sessions").get().await().toObjects(Session::class.java)
            )
        }
        return list
    }

    override suspend fun getClientsAndSessions(): Map<Client, List<Session>> = coroutineScope {
        val clientsSnap = clientsCollection.whereNotEqualTo("status", "REMOVED").get().await()
        val clients = clientsSnap.toObjects(Client::class.java)
        clients.map { client ->
            async {
                client to clientsCollection.document(client.id).collection("sessions").get().await()
                    .toObjects(Session::class.java)
            }
        }.awaitAll().toMap()
    }

    // --- NEW IMPLEMENTATION ---

    override suspend fun logSessionHistory(clientId: String, sessions: List<Session>, date: Long) {
        val batch = firestore.batch()
        val historyCollection = clientsCollection.document(clientId).collection("history")

        sessions.forEach { session ->
            // Only log sessions that had exercises marked as done
            if (session.exercises.any { it.isDone }) {
                // Create a history record. We append the timestamp to the ID to make it unique per week
                val historyId = "${session.id}_$date"
                // We add the 'lastResetTimestamp' to the session object so we know when it happened
                // reusing the field or adding a new logic. Ideally, Session object is generic.
                // We will save the Session object as is, but the Document ID tells us the time.
                // Or better, we trust the `lastResetTimestamp` inside the Session object if we updated it before calling this.
                // Actually, the `sessions` passed here are the OLD ones (completed).

                // Let's create a copy with the specific timestamp for this log entry
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
