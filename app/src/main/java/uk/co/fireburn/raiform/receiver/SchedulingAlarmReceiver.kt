package uk.co.fireburn.raiform.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import uk.co.fireburn.raiform.MainActivity

class SchedulingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigation_target", "scheduler")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use system icon to avoid build errors if R class generation is flaky
        val iconRes = android.R.drawable.ic_lock_idle_alarm

        val notification = NotificationCompat.Builder(context, "scheduling_channel")
            .setSmallIcon(iconRes)
            .setContentTitle("Scheduling Time!")
            .setContentText("Time to plan the week ahead.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
