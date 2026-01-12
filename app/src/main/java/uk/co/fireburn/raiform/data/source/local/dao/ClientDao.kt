package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.ClientEntity
import uk.co.fireburn.raiform.domain.model.ClientStatus

@Dao
interface ClientDao {

    // UI Query: Hide deleted clients
    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY dateAdded DESC")
    fun getAllClients(): Flow<List<ClientEntity>>

    // Sync Query: Get everything (including deleted) changed since timestamp
    @Query("SELECT * FROM clients WHERE lastSyncTimestamp > :timestamp")
    suspend fun getClientsForSync(timestamp: Long): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE status = :status AND isDeleted = 0 ORDER BY dateAdded DESC")
    fun getClientsByStatus(status: ClientStatus): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId AND isDeleted = 0")
    fun getClientById(clientId: String): Flow<ClientEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClients(clients: List<ClientEntity>)

    // Return Int (number of rows updated) to handle upsert logic in Repo
    @Update
    suspend fun updateClient(client: ClientEntity): Int

    @Query("UPDATE clients SET status = :status, lastSyncTimestamp = :timestamp WHERE id = :clientId")
    suspend fun updateClientStatus(clientId: String, status: ClientStatus, timestamp: Long)

    // SOFT DELETE: Mark as deleted and update timestamp so Sync picks it up
    @Query("UPDATE clients SET isDeleted = 1, lastSyncTimestamp = :timestamp WHERE id = :clientId")
    suspend fun softDeleteClient(clientId: String, timestamp: Long)

    // Hard delete (Legacy/Cleanup)
    @Query("DELETE FROM clients WHERE id = :clientId")
    suspend fun deleteClient(clientId: String)
}
