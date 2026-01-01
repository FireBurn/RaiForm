package uk.co.fireburn.raiform.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val schedulingDay: Flow<Int>
    suspend fun setSchedulingDay(day: Int)
}
