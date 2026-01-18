package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseDefinitionEntity

@Dao
interface ExerciseDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinition(definition: ExerciseDefinitionEntity)

    @Query("SELECT * FROM exercise_definitions WHERE name = :name")
    suspend fun getDefinition(name: String): ExerciseDefinitionEntity?

    @Query("UPDATE exercise_definitions SET name = :newName WHERE name = :oldName")
    suspend fun updateName(oldName: String, newName: String)

    @Query("SELECT * FROM exercise_definitions")
    suspend fun getAllDefinitions(): List<ExerciseDefinitionEntity>
}
