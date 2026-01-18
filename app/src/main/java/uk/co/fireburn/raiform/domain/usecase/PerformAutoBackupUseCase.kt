package uk.co.fireburn.raiform.domain.usecase

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.model.ExportData
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.DataExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class PerformAutoBackupUseCase @Inject constructor(
    private val repository: RaiRepository,
    private val dataExporter: DataExporter,
    @param:ApplicationContext private val context: Context
) {
    suspend operator fun invoke(): Result<String> {
        return try {
            val clients = repository.getAllClients().first()
            val sessions = repository.getAllSessions().first()
            val allHistoryLogs = repository.getAllHistoryLogs().first()
            val definitions = repository.getAllExerciseBodyParts().first()
            val allMeasurements = clients.flatMap { client ->
                repository.getBodyMeasurements(client.id).first()
            }

            val exportData = ExportData(
                clients = clients,
                sessions = sessions,
                historyLogs = allHistoryLogs,
                bodyMeasurements = allMeasurements,
                exerciseDefinitions = definitions
            )

            // Create filename: RaiForm_AutoBackup_2023-10-27.json
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val fileName = "RaiForm_AutoBackup_$dateStr.json"

            // Save to: Android/data/uk.co.fireburn.raiform/files/Documents/Backups/
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val backupDir = File(docsDir, "Backups")
            val file = File(backupDir, fileName)

            dataExporter.exportToFile(exportData, file)

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
