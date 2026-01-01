package uk.co.fireburn.raiform.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import uk.co.fireburn.raiform.MainActivity
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import java.time.LocalDateTime

class RaiFormWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private val keyTarget = ActionParameters.Key<String>("navigation_target")
    private val keyClient = ActionParameters.Key<String>("client_id")
    private val keySession = ActionParameters.Key<String>("session_id")

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepositoryEntryPoint {
        fun clientRepository(): ClientRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            RepositoryEntryPoint::class.java
        )
        val repository = entryPoint.clientRepository()
        val settingsRepo = entryPoint.settingsRepository()

        val clients = repository.getClients().firstOrNull() ?: emptyList()
        val schedulingDay = settingsRepo.schedulingDay.firstOrNull() ?: 7

        val sessionOwnerMap = mutableMapOf<String, Client>()
        val todaysSessions = mutableListOf<Session>()

        val now = LocalDateTime.now()
        val todayDow = now.dayOfWeek.value

        for (client in clients) {
            val clientSessions =
                repository.getSessionsForClient(client.id).firstOrNull() ?: emptyList()
            clientSessions.forEach { session ->
                if (session.scheduledDay == todayDow && !session.isSkippedThisWeek) {
                    todaysSessions.add(session)
                    sessionOwnerMap[session.id] = client
                }
            }
        }

        todaysSessions.sortBy { it.scheduledHour ?: 24 }

        val isSchedulingDay = (todayDow == schedulingDay)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    sessions = todaysSessions,
                    sessionOwnerMap = sessionOwnerMap,
                    isSchedulingDay = isSchedulingDay
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        sessions: List<Session>,
        sessionOwnerMap: Map<String, Client>,
        isSchedulingDay: Boolean
    ) {
        val size = LocalSize.current
        val isSmall = size.height < 180.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (sessions.isEmpty()) {
                EmptyState(context, isSchedulingDay)
            } else {
                if (isSmall) {
                    val nextSession = findNextSession(sessions) ?: sessions.first()
                    val client = sessionOwnerMap[nextSession.id]

                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(
                                actionStartActivity(
                                    componentName = ComponentName(
                                        context,
                                        MainActivity::class.java
                                    ),
                                    parameters = actionParametersOf(
                                        keyTarget to "session",
                                        keyClient to (client?.id ?: ""),
                                        keySession to nextSession.id
                                    )
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatTime(
                                nextSession.scheduledHour ?: 0,
                                nextSession.scheduledMinute ?: 0
                            ),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFF7D02C)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = client?.name ?: "Unknown",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = nextSession.name,
                            style = TextStyle(
                                color = ColorProvider(Color.Gray),
                                fontSize = 14.sp
                            )
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(sessions) { session ->
                            val client = sessionOwnerMap[session.id]

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color(0xFF1E1E1E))
                                    .padding(8.dp)
                                    .clickable(
                                        actionStartActivity(
                                            componentName = ComponentName(
                                                context,
                                                MainActivity::class.java
                                            ),
                                            parameters = actionParametersOf(
                                                keyTarget to "session",
                                                keyClient to (client?.id ?: ""),
                                                keySession to session.id
                                            )
                                        )
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(
                                        session.scheduledHour ?: 0,
                                        session.scheduledMinute ?: 0
                                    ),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFF7D02C)),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(GlanceModifier.width(12.dp))
                                Column {
                                    Text(
                                        text = client?.name ?: "Unknown",
                                        style = TextStyle(
                                            color = ColorProvider(Color.White),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = session.name,
                                        style = TextStyle(
                                            color = ColorProvider(Color.Gray),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState(context: Context, isSchedulingDay: Boolean) {
        val target = if (isSchedulingDay) "scheduler" else "dashboard"
        val message = if (isSchedulingDay) "Scheduling Time" else "Day Off!"
        val icon = if (isSchedulingDay) "📅" else "🎉"
        val color = if (isSchedulingDay) Color(0xFFF7D02C) else Color(0xFF69F0AE)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(
                    actionStartActivity(
                        componentName = ComponentName(context, MainActivity::class.java),
                        parameters = actionParametersOf(keyTarget to target)
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, style = TextStyle(fontSize = 32.sp))
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = message,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            if (!isSchedulingDay) {
                Text(
                    text = "Rest & Recover",
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp)
                )
            }
        }
    }

    private fun findNextSession(sessions: List<Session>): Session? {
        val now = LocalDateTime.now()
        return sessions.firstOrNull {
            val h = it.scheduledHour ?: 0
            val m = it.scheduledMinute ?: 0
            (h > now.hour) || (h == now.hour && m > now.minute)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "pm" else "am"
        val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return if (minute == 0) "$h$amPm" else String.format("%d:%02d%s", h, minute, amPm)
    }
}
