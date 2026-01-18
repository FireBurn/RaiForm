package uk.co.fireburn.raiform.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.model.ExportData
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.DataExporter
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val repository: RaiRepository,
    private val dataExporter: DataExporter
) {
    suspend operator fun invoke(outputUri: Uri): Result<Unit> {
        return try {
            // 1. Fetch Core Data
            val clients = repository.getAllClients().first()
            val sessions = repository.getAllSessions().first()
            val allHistoryLogs = repository.getAllHistoryLogs().first()

            // 2. Fetch Global Exercise Definitions (Body Parts)
            val definitions = repository.getAllExerciseBodyParts().first()

            // 3. Fetch Body Measurements (Iterate clients to gather all)
            val allMeasurements = clients.flatMap { client ->
                repository.getBodyMeasurements(client.id).first()
            }

            // 4. Construct Export Object
            val exportData = ExportData(
                clients = clients,
                sessions = sessions,
                historyLogs = allHistoryLogs,
                bodyMeasurements = allMeasurements,
                exerciseDefinitions = definitions
            )

            // 5. Write to file
            dataExporter.exportToJson(exportData, outputUri)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
