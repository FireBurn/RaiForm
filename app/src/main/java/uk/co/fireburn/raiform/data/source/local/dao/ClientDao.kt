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

    @Query("SELECT * FROM clients ORDER BY dateAdded DESC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE status = :status ORDER BY dateAdded DESC")
    fun getClientsByStatus(status: ClientStatus): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId")
    fun getClientById(clientId: String): Flow<ClientEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClients(clients: List<ClientEntity>)

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("UPDATE clients SET status = :status WHERE id = :clientId")
    suspend fun updateClientStatus(clientId: String, status: ClientStatus)

    @Query("DELETE FROM clients WHERE id = :clientId")
    suspend fun deleteClient(clientId: String)
}
