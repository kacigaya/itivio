package app.itivio.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DisruptionsTest {
    private val closed = Alert("Line closed", "", effect = AlertEffect.NO_SERVICE)
    private val detour = Alert("Detour", "", effect = AlertEffect.DETOUR)
    private val lift = Alert("Lift out of service", "", effect = AlertEffect.ACCESSIBILITY_ISSUE)
    private val notice = Alert("New timetable", "")

    @Test
    fun `an alert on more than one stop shows one time with all its stops and lines`() {
        val disruptions = disruptionsOf(
            listOf(
                departure("Zoo", "M41", detour),
                departure("Central", "M41", detour),
                departure("Central", "M2", detour),
                departure("Central", "U2"),
            ),
        )

        val disruption = disruptions.single()
        assertEquals(detour, disruption.alert)
        assertEquals(listOf("Zoo", "Central"), disruption.stops)
        assertEquals(listOf("M41", "M2"), disruption.lines.map { it.line.name })
    }

    @Test
    fun `the most severe disruptions come first`() {
        val rated = notice.copy(header = "Rated severe", severity = AlertSeverity.SEVERE)
        val disruptions = disruptionsOf(
            listOf(departure("Zoo", "M41", notice, lift, detour), departure("Zoo", "U2", closed, rated)),
        )

        // No service is severe without a rating. Among equal levels, the stronger effect is first.
        assertEquals(listOf(closed, rated, detour, lift, notice), disruptions.map { it.alert })
    }

    @Test
    fun `the rating of the provider counts before the effect`() {
        assertEquals(AlertSeverity.INFO, closed.copy(severity = AlertSeverity.INFO).level)
        assertEquals(AlertSeverity.SEVERE, closed.level)
        assertEquals(AlertSeverity.WARNING, detour.level)
        assertEquals(AlertSeverity.INFO, lift.level)
        assertEquals(AlertSeverity.UNKNOWN, notice.level)
    }

    @Test
    fun `alerts of an earlier group are left out`() {
        val departures = listOf(departure("Zoo", "M41", detour, lift))

        assertEquals(listOf(lift), disruptionsOf(departures, shown = setOf(detour)).map { it.alert })
    }

    private fun departure(stop: String, line: String, vararg alerts: Alert) = Departure(
        stop = Stop(stop, GeoPoint(52.5, 13.4), stopId = stop),
        line = Line(line),
        mode = TransitMode.BUS,
        headsign = "Town hall",
        time = StopTime(Instant.parse("2026-11-09T10:00:00Z")),
        cancelled = false,
        tripId = "trip-$line",
        alerts = alerts.toList(),
    )
}
