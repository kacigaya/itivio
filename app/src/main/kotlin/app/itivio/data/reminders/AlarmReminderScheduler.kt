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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reminders with [AlarmManager]. The alarm is exact when the app may set exact alarms; otherwise
 * Android can deliver it some minutes late, in doze. The text of the notification is written when
 * the alarm is set, so the receiver needs no data of the app. The reminder is also stored, so it
 * survives process death and a reboot ([restore]).
 */
internal class AlarmReminderScheduler(
    private val context: Context,
    private val storage: ReminderStorage,
    private val clock: Clock = Clock.systemUTC(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val state = MutableStateFlow<Reminder?>(null)
    override val active: StateFlow<Reminder?> = state.asStateFlow()

    init {
        // The alarm itself survives process death. A reboot clears it, and ReminderBootReceiver
        // sets it again from the stored reminder.
        scope.launch {
            val stored = storage.read() ?: return@launch
            if (clock.instant().isBefore(stored.reminder.at)) state.value = stored.reminder else storage.clear()
        }
    }

    override fun schedule(journey: Journey, leadMinutes: Int) {
        val leave = journey.departure.best
        val at = maxOf(leave.minusSeconds(leadMinutes * 60L), clock.instant())
        val first = journey.legs.firstOrNull { it is Leg.Transit } as Leg.Transit?
        val text = if (first != null) {
            context.getString(R.string.reminder_text, first.line.name, first.headsign ?: first.to.name, first.from.name, time(first.start.best))
        } else {
            context.getString(R.string.reminder_text_walk, time(leave))
        }
        setAlarm(at, title(leadMinutes), text)
        val reminder = Reminder(journey.id, leadMinutes, at)
        state.value = reminder
        scope.launch { storage.save(StoredReminder(reminder, text)) }
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
        scope.launch { storage.clear() }
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override suspend fun restore() {
        val stored = storage.read() ?: return
        // A reminder that passed while the device was off is not set again.
        if (!clock.instant().isBefore(stored.reminder.at)) {
            storage.clear()
            return
        }
        setAlarm(stored.reminder.at, title(stored.reminder.leadMinutes), stored.text)
        state.value = stored.reminder
    }

    /**
     * Android 14 and later deny exact alarms by default, and the grant can be taken away at any
     * time, so a refusal falls back to an inexact alarm instead of setting none.
     */
    private fun setAlarm(at: Instant, title: String, text: String) {
        val intent = pendingIntent(
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.TITLE, title).putExtra(ReminderReceiver.TEXT, text),
        )
        if (!canScheduleExact()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
            return
        }
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private fun title(leadMinutes: Int): String = if (leadMinutes == 0) {
        context.getString(R.string.reminder_title_now)
    } else {
        context.resources.getQuantityString(R.plurals.reminder_title, leadMinutes, leadMinutes)
    }

    private fun pendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(instant))
}
