package app.itivio.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import app.itivio.R
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Reminder
import app.itivio.domain.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reminders with [AlarmManager]. The alarm is exact when the app may set exact alarms; otherwise
 * Android can deliver it some minutes late, in doze. The text of the notification is written when
 * the alarm is set, so the receiver needs no data of the app.
 */
internal class AlarmReminderScheduler(private val context: Context, private val clock: Clock = Clock.systemUTC()) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val state = MutableStateFlow<Reminder?>(null)
    override val active: StateFlow<Reminder?> = state.asStateFlow()

    override fun schedule(journey: Journey, leadMinutes: Int) {
        val leave = journey.departure.best
        val at = maxOf(leave.minusSeconds(leadMinutes * 60L), clock.instant())
        val title = if (leadMinutes == 0) {
            context.getString(R.string.reminder_title_now)
        } else {
            context.resources.getQuantityString(R.plurals.reminder_title, leadMinutes, leadMinutes)
        }
        val first = journey.legs.firstOrNull { it is Leg.Transit } as Leg.Transit?
        val text = if (first != null) {
            context.getString(R.string.reminder_text, first.line.name, first.headsign ?: first.to.name, first.from.name, time(first.start.best))
        } else {
            context.getString(R.string.reminder_text_walk, time(leave))
        }
        val intent = pendingIntent(Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.TITLE, title).putExtra(ReminderReceiver.TEXT, text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
        state.value = Reminder(journey.id, leadMinutes, at)
    }

    override fun update(journey: Journey) {
        val reminder = state.value ?: return
        // After it went off, a new time must not set it again.
        if (reminder.journeyId != journey.id || !clock.instant().isBefore(reminder.at)) return
        schedule(journey, reminder.leadMinutes)
    }

    override fun cancel() {
        alarms.cancel(pendingIntent(Intent(context, ReminderReceiver::class.java)))
        state.value = null
    }

    private fun pendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(instant))
}
