package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val schedulingDay: Flow<Int>
    val schedulingHour: Flow<Int>
    val schedulingMinute: Flow<Int>

    suspend fun setSchedulingDay(day: Int)
    suspend fun setSchedulingTime(hour: Int, minute: Int)
}
