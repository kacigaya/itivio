package app.itivio.data

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.itivio.data.reminders.AlarmReminderScheduler
import app.itivio.data.reminders.ReminderReceiver
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlarmReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = Instant.parse("2026-11-24T08:40:00Z")
    private val leave = Instant.parse("2026-11-24T08:58:00Z")

    private fun journey(delaySeconds: Long = 0): Journey {
        val from = Stop("S+U Alexanderplatz", GeoPoint(52.52, 13.41))
        val to = Stop("Potsdamer Platz", GeoPoint(52.51, 13.37))
        val start = StopTime(leave, leave.plusSeconds(delaySeconds))
        val end = StopTime(leave.plusSeconds(1_200), leave.plusSeconds(1_200 + delaySeconds))
        return Journey(
            id = "j",
            legs = listOf(Leg.Transit(TransitMode.SUBWAY, from, to, start, end, emptyList(), Line("U2"), "Ruhleben", "t1", emptyList())),
            departure = start,
            arrival = end,
            transfers = 0,
        )
    }

    private fun scheduler(at: Instant = now) = AlarmReminderScheduler(context, Clock.fixed(at, ZoneOffset.UTC))

    @Test
    fun `a reminder goes off its lead time before the departure and carries its text`() {
        val reminders = scheduler()
        reminders.schedule(journey(), leadMinutes = 5)

        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(leave.minusSeconds(300).toEpochMilli(), alarm.triggerAtTime)
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals("Leave in 5 minutes", intent.getStringExtra(ReminderReceiver.TITLE))
        assertEquals(leave.minusSeconds(300), reminders.active.value?.at)
    }

    @Test
    fun `a live delay moves the reminder, and cancel removes it`() {
        val reminders = scheduler()
        reminders.schedule(journey(), leadMinutes = 5)
        reminders.update(journey(delaySeconds = 120))
        assertEquals(leave.plusSeconds(120 - 300).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)

        reminders.cancel()
        assertNull(alarms.peekNextScheduledAlarm())
        assertNull(reminders.active.value)
    }

    @Test
    fun `a reminder that went off is not set again`() {
        scheduler().schedule(journey(), leadMinutes = 5)
        val later = scheduler(at = leave)
        later.schedule(journey(), leadMinutes = 5)
        val fired = later.active.value
        later.update(journey(delaySeconds = 600))
        assertEquals(fired, later.active.value)
    }
}
