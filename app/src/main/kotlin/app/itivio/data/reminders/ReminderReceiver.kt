package app.itivio.data.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.itivio.R

/** Shows the departure reminder. Without the notification permission, it shows nothing. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.reminder_channel))
                .build(),
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(intent.getStringExtra(TITLE))
            .setContentText(intent.getStringExtra(TEXT))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(TEXT)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    internal companion object {
        const val TITLE = "title"
        const val TEXT = "text"
        const val CHANNEL = "departure_reminders"
        const val NOTIFICATION_ID = 1
    }
}
