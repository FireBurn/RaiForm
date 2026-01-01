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

    override val schedulingDay: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SCHEDULING_DAY_KEY] ?: 7 // Default to Sunday (7)
        }

    override suspend fun setSchedulingDay(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[SCHEDULING_DAY_KEY] = day
        }
        // Update widget so it immediately reflects "Scheduling Time" if today becomes the day
        widgetUpdater.triggerUpdate()
    }
}
