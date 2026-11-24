package app.itivio.domain

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** A reminder to leave for the journey [journeyId], [leadMinutes] before its departure, [at] that time. */
data class Reminder(val journeyId: String, val leadMinutes: Int, val at: Instant)

/**
 * Departure reminders. One at a time, on the device: a reminder sends nothing over the network.
 * While the itinerary is on screen, [update] moves the reminder with the live times of the journey.
 * The reminder is kept across process death and a reboot, and Android shows no reminder without the
 * notification permission.
 */
interface ReminderScheduler {
    val active: StateFlow<Reminder?>

    /** Sets the reminder of [journey], [leadMinutes] before it leaves. It replaces any other reminder. */
    fun schedule(journey: Journey, leadMinutes: Int)

    /** Moves the reminder of [journey] to its new departure. Does nothing for another journey or after the reminder went off. */
    fun update(journey: Journey)

    fun cancel()

    /** True when Android delivers a reminder at its exact time. Android 14 and later deny this by default. */
    fun canScheduleExact(): Boolean

    /** Sets the alarm of the stored reminder again after a reboot, which clears all alarms. */
    suspend fun restore()

    companion object {
        /** The lead times the itinerary offers, in minutes. 0 is the time to leave. */
        val LEAD_MINUTES = listOf(0, 2, 5, 10)
    }
}
