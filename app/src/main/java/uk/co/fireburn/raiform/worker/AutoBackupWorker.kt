package uk.co.fireburn.raiform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import uk.co.fireburn.raiform.domain.usecase.PerformAutoBackupUseCase
import java.time.LocalDateTime

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val performAutoBackupUseCase: PerformAutoBackupUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Check if today is the Scheduling Day
            val preferredDay = settingsRepository.schedulingDay.first()
            val today = LocalDateTime.now().dayOfWeek.value // 1 (Mon) to 7 (Sun)

            if (today != preferredDay) {
                // Not the right day, do nothing but return success so it reschedules for tomorrow
                return Result.success()
            }

            // 2. Perform Backup
            val result = performAutoBackupUseCase()

            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup_worker"
    }
}
