package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.HistoryEntity

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_logs WHERE clientId = :clientId ORDER BY dateLogged DESC")
    fun getHistoryForClient(clientId: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_logs ORDER BY dateLogged DESC")
    fun getAllHistoryLogs(): Flow<List<HistoryEntity>> // ADDED

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<HistoryEntity>)
}
