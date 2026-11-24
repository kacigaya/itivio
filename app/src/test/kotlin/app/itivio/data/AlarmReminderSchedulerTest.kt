package app.itivio.data

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.itivio.data.reminders.AlarmReminderScheduler
import app.itivio.data.reminders.ReminderReceiver
import app.itivio.data.reminders.ReminderStorage
import app.itivio.data.reminders.StoredReminder
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.Reminder
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlarmReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = Instant.parse("2026-11-24T08:40:00Z")
    private val leave = Instant.parse("2026-11-24T08:58:00Z")
    // The storage and the restore run on this scope, so a test sees their result at once.
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    /** The `settings` file of one run of the app. */
    private class FakeStorage(var stored: StoredReminder? = null) : ReminderStorage {
        override suspend fun read(): StoredReminder? = stored

        override suspend fun save(stored: StoredReminder) {
            this.stored = stored
        }

        override suspend fun clear() {
            stored = null
        }
    }

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

    private fun scheduler(at: Instant = now, storage: ReminderStorage = FakeStorage()) =
        AlarmReminderScheduler(context, storage, Clock.fixed(at, ZoneOffset.UTC), scope)

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

    @Test
    fun `a reminder survives process death`() {
        val storage = FakeStorage()
        scheduler(storage = storage).schedule(journey(), leadMinutes = 5)

        // A new process reads the same settings file.
        val restarted = scheduler(storage = storage)
        assertEquals(Reminder("j", 5, leave.minusSeconds(300)), restarted.active.value)
        assertTrue(storage.stored?.text?.startsWith("U2 towards Ruhleben") == true)
    }

    @Test
    fun `a reminder that went off while the app was closed is dropped`() {
        val storage = FakeStorage(StoredReminder(Reminder("j", 5, leave.minusSeconds(300)), "Leave in 5 minutes"))

        val restarted = scheduler(at = leave, storage = storage)
        assertNull(restarted.active.value)
        assertNull(storage.stored)
    }

    @Test
    fun `a reboot sets the stored reminder again`() = runBlocking {
        val storage = FakeStorage(StoredReminder(Reminder("j", 5, leave.minusSeconds(300)), "U2 towards Ruhleben"))
        // A reboot clears every alarm, and the new process sets none by itself.
        val restarted = scheduler(storage = storage)
        assertNull(alarms.peekNextScheduledAlarm())

        restarted.restore()

        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(leave.minusSeconds(300).toEpochMilli(), alarm.triggerAtTime)
        assertEquals("U2 towards Ruhleben", shadowOf(alarm.operation).savedIntent.getStringExtra(ReminderReceiver.TEXT))
    }

    @Test
    fun `without exact alarms the reminder is still set`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        try {
            val reminders = scheduler()
            assertFalse(reminders.canScheduleExact())

            reminders.schedule(journey(), leadMinutes = 5)

            assertEquals(leave.minusSeconds(300).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)
            assertNotNull(reminders.active.value)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }
}
