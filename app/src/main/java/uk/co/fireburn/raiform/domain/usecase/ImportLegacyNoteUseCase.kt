package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.model.BodyMeasurement
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.LegacyParser
import javax.inject.Inject

class ImportLegacyNoteUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    /**
     * Preview: Parses text without saving.
     */
    fun preview(rawText: String): LegacyParser.ParseResult {
        return LegacyParser.parseLegacyNote(rawText)
    }

    /**
     * Commit: Parses and saves to Database.
     */
    suspend operator fun invoke(rawText: String): Result<String> {
        return try {
            // 1. Parse
            val parseResult = LegacyParser.parseLegacyNote(rawText)

            // 2. Create Client
            val newClient = Client(name = parseResult.clientName)
            repository.saveClient(newClient)

            // 3. Save Exercise Definitions (Body Parts)
            parseResult.exerciseBodyParts.forEach { (name, bodyPart) ->
                repository.saveExerciseDefinition(name, bodyPart)
            }

            // 4. Save Sessions
            parseResult.sessions.forEach { session ->
                repository.saveSession(session.copy(clientId = newClient.id))
            }

            // 5. Save Measurements
            if (parseResult.measurements.isNotEmpty()) {
                // Consolidate into a single measurement entry for "today"
                // The parser returns a list of raw (Type, Value).
                // We need to map these types to the BodyMeasurement fields.

                var weight: Double? = null
                var waist: Double? = null
                var chest: Double? = null
                var arms: Double? = null
                var legs: Double? = null
                var shoulders: Double? = null

                parseResult.measurements.forEach { raw ->
                    when {
                        raw.type.contains("Weight", true) -> weight = raw.value
                        raw.type.contains("Waist", true) -> waist = raw.value
                        raw.type.contains("Chest", true) -> chest = raw.value
                        raw.type.contains("Arm", true) -> arms = raw.value
                        raw.type.contains("Leg", true) -> legs = raw.value
                        raw.type.contains("Shoulder", true) -> shoulders = raw.value
                    }
                }

                val measurement = BodyMeasurement(
                    clientId = newClient.id,
                    dateRecorded = System.currentTimeMillis(),
                    weightKg = weight,
                    waistCm = waist,
                    chestCm = chest,
                    armsCm = arms,
                    legsCm = legs,
                    shouldersCm = shoulders
                )
                repository.saveBodyMeasurement(measurement)
            }

            Result.success(newClient.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
