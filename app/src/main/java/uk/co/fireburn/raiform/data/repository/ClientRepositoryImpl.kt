package uk.co.fireburn.raiform.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.ClientStatus
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ClientRepository {

    private val clientsCollection = firestore.collection("clients")

    override fun getClients(): Flow<List<Client>> = callbackFlow {
        // Subscribe to real-time updates
        val listener = clientsCollection
            .whereNotEqualTo("status", "REMOVED") // Filter out archived clients
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error) // Close stream on error
                    return@addSnapshotListener
                }

                val clients = snapshot?.documents?.mapNotNull { doc ->
                    // Manual mapping to ensure Domain integrity
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

                    Client(id, name, status, notes, dateAdded)
                } ?: emptyList()

                trySend(clients)
            }

        // Unregister listener when the Flow is cancelled (e.g., user leaves screen)
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
            dateAdded = snapshot.getLong("dateAdded") ?: 0L
        )
    }

    override suspend fun saveClient(client: Client) {
        clientsCollection.document(client.id).set(client).await()
    }

    override suspend fun archiveClient(clientId: String) {
        clientsCollection.document(clientId)
            .update("status", ClientStatus.REMOVED.name)
            .await()
    }

    override suspend fun saveClientWithSessions(client: Client, sessions: List<Session>) {
        val batch = firestore.batch()

        // 1. Set Client Ref
        val clientRef = clientsCollection.document(client.id)
        batch.set(clientRef, client)

        // 2. Set Sessions (Sub-collection)
        val sessionsCollection = clientRef.collection("sessions")
        sessions.forEach { session ->
            val sessionRef = sessionsCollection.document(session.id)
            batch.set(sessionRef, session)
        }

        // 3. Commit atomically
        batch.commit().await()
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
                    doc.toObject(Session::class.java) // POKO auto-mapping works well for simple nested lists
                } ?: emptyList()

                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateSession(clientId: String, session: Session) {
        firestore.collection("clients")
            .document(clientId)
            .collection("sessions")
            .document(session.id)
            .set(session) // Overwrites the session with new exercise data
            .await()
    }

    override suspend fun deleteSession(clientId: String, sessionId: String) {
        clientsCollection.document(clientId)
            .collection("sessions")
            .document(sessionId)
            .delete()
            .await()
    }
}
