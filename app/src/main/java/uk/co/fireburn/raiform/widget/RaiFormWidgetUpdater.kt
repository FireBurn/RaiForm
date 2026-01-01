package uk.co.fireburn.raiform.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaiFormWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun triggerUpdate() {
        RaiFormWidget().updateAll(context)
    }
}
