package uk.co.fireburn.raiform.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import uk.co.fireburn.raiform.receiver.SchedulingAlarmReceiver
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(dayOfWeek: Int, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val targetDay = DayOfWeek.of(dayOfWeek)

        var nextTime = now.with(TemporalAdjusters.nextOrSame(targetDay))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)

        if (nextTime.isBefore(now)) {
            nextTime = nextTime.plusWeeks(1)
        }

        val millis = nextTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, SchedulingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    millis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
    }
}
