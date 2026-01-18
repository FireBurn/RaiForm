package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.BodyMeasurementEntity

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM body_measurements WHERE clientId = :clientId ORDER BY dateRecorded DESC")
    fun getMeasurementsForClient(clientId: String): Flow<List<BodyMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: String)
}
