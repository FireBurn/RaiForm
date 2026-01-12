package uk.co.fireburn.raiform.data.source.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import uk.co.fireburn.raiform.domain.model.ClientStatus
import uk.co.fireburn.raiform.domain.model.Exercise

class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- ClientStatus Enum ---
    @TypeConverter
    fun fromClientStatus(status: ClientStatus): String {
        return status.name
    }

    @TypeConverter
    fun toClientStatus(value: String): ClientStatus {
        return try {
            ClientStatus.valueOf(value)
        } catch (e: Exception) {
            ClientStatus.ACTIVE // Fallback
        }
    }

    // --- List<Exercise> (JSON) for History ---
    @TypeConverter
    fun fromExerciseList(exercises: List<Exercise>?): String {
        if (exercises == null) return "[]"
        return try {
            json.encodeToString(exercises)
        } catch (e: Exception) {
            "[]"
        }
    }

    @TypeConverter
    fun toExerciseList(value: String?): List<Exercise> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Exercise>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
