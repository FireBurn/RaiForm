package uk.co.fireburn.raiform.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import uk.co.fireburn.raiform.widget.RaiFormWidget
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository {

    private val SCHEDULING_DAY_KEY = intPreferencesKey("scheduling_day")
    private val SCHEDULING_HOUR_KEY = intPreferencesKey("scheduling_hour")
    private val SCHEDULING_MINUTE_KEY = intPreferencesKey("scheduling_minute")

    override val schedulingDay: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_DAY_KEY] ?: 7 } // Default Sunday

    override val schedulingHour: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_HOUR_KEY] ?: 9 } // Default 9 AM

    override val schedulingMinute: Flow<Int> =
        context.dataStore.data.map { it[SCHEDULING_MINUTE_KEY] ?: 0 }

    override suspend fun setSchedulingDay(day: Int) {
        context.dataStore.edit { it[SCHEDULING_DAY_KEY] = day }
        updateWidget()
    }

    override suspend fun setSchedulingTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[SCHEDULING_HOUR_KEY] = hour
            it[SCHEDULING_MINUTE_KEY] = minute
        }
        updateWidget()
    }

    private suspend fun updateWidget() {
        try {
            RaiFormWidget().updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
