package uk.co.fireburn.raiform.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import uk.co.fireburn.raiform.widget.RaiFormWidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetUpdater: RaiFormWidgetUpdater
) : SettingsRepository {

    private val SCHEDULING_DAY_KEY = intPreferencesKey("scheduling_day")
    private val SCHEDULING_HOUR_KEY = intPreferencesKey("scheduling_hour")
    private val SCHEDULING_MINUTE_KEY = intPreferencesKey("scheduling_minute")

    override val schedulingDay: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_DAY_KEY] ?: 7 }
    override val schedulingHour: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_HOUR_KEY] ?: 9 } // Default 9 AM
    override val schedulingMinute: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_MINUTE_KEY] ?: 0 }

    override suspend fun setSchedulingDay(day: Int) {
        context.dataStore.edit { it[SCHEDULING_DAY_KEY] = day }
        widgetUpdater.triggerUpdate()
    }

    override suspend fun setSchedulingTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[SCHEDULING_HOUR_KEY] = hour
            it[SCHEDULING_MINUTE_KEY] = minute
        }
        // No widget update needed for time change, only day
    }
}
