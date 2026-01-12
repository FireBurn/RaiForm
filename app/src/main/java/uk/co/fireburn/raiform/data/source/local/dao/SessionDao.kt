package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity
import uk.co.fireburn.raiform.data.source.local.relation.SessionPopulated

@Dao
interface SessionDao {

    // --- Reads (UI ignores deleted) ---

    @Transaction
    @Query("SELECT * FROM sessions WHERE clientId = :clientId AND isDeleted = 0 ORDER BY scheduledDay ASC, scheduledHour ASC")
    fun getSessionsForClient(clientId: String): Flow<List<SessionPopulated>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId AND isDeleted = 0")
    fun getSessionById(sessionId: String): Flow<SessionPopulated?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE isDeleted = 0")
    fun getAllSessions(): Flow<List<SessionPopulated>>

    // --- Sync Query (Includes deleted) ---
    // Note: We return SessionEntity here (flat), not Populated, for simpler syncing
    @Query("SELECT * FROM sessions WHERE lastSyncTimestamp > :timestamp")
    suspend fun getSessionsForSync(timestamp: Long): List<SessionEntity>

    // --- Write ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    // Used by Sync Pull
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    // SOFT DELETE
    @Query("UPDATE sessions SET isDeleted = 1, lastSyncTimestamp = :timestamp WHERE id = :sessionId")
    suspend fun softDeleteSession(sessionId: String, timestamp: Long)

    // Hard Delete (Only for cleanup if needed later)
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM sessions WHERE clientId = :clientId")
    suspend fun deleteSessionsForClient(clientId: String)

    // --- Template Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplateEntity)

    @Update
    suspend fun updateTemplate(template: ExerciseTemplateEntity)

    @Query("SELECT * FROM exercise_templates WHERE clientId = :clientId AND name = :name LIMIT 1")
    suspend fun findTemplateByName(clientId: String, name: String): ExerciseTemplateEntity?

    @Query("SELECT * FROM exercise_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: String): ExerciseTemplateEntity?

    // --- Link Management ---

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun getLinksForSession(sessionId: String): List<SessionExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SessionExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<SessionExerciseEntity>)

    @Update
    suspend fun updateLink(link: SessionExerciseEntity)

    @Query("SELECT * FROM session_exercises WHERE id = :linkId")
    suspend fun getLinkById(linkId: String): SessionExerciseEntity?

    @Query("UPDATE session_exercises SET isDone = :isDone WHERE id = :linkId")
    suspend fun updateLinkStatus(linkId: String, isDone: Boolean)

    @Delete
    suspend fun deleteLink(link: SessionExerciseEntity)

    @Delete
    suspend fun deleteLinks(links: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun clearLinksForSession(sessionId: String)
}
