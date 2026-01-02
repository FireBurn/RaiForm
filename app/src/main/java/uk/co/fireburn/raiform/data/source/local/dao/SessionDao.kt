package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE clientId = :clientId ORDER BY scheduledDay ASC, scheduledHour ASC")
    fun getSessionsForClient(clientId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: String): Flow<SessionEntity?>

    /**
     * Gets all sessions for the Global Scheduler.
     * Note: This might fetch a lot of data, but in Room it's fast.
     */
    @Query("SELECT * FROM sessions")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM sessions WHERE clientId = :clientId")
    suspend fun deleteSessionsForClient(clientId: String)
}
