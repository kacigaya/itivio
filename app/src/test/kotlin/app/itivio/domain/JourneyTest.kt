package app.itivio.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyTest {
    private val base = Instant.parse("2026-11-09T10:00:00Z")
    private fun at(minutes: Long) = StopTime(base.plusSeconds(minutes * 60))
    private val stop = Stop("A", GeoPoint(52.5, 13.4))

    // Walk 0-5, wait 5-8, ride 8-20 with a live arrival at 22.
    private val journey = Journey(
        id = "j",
        legs = listOf(
            Leg.Street(TransitMode.WALK, stop, stop, at(0), at(5), emptyList(), 300.0),
            Leg.Transit(
                TransitMode.BUS, stop, stop, at(8), StopTime(at(20).scheduled, at(22).scheduled),
                emptyList(), Line("M41"), "Town hall", "trip", emptyList(),
            ),
        ),
        departure = at(0),
        arrival = StopTime(at(20).scheduled, at(22).scheduled),
        transfers = 0,
    )

    @Test
    fun `no leg is current before the departure and from the arrival on`() {
        assertNull(journey.currentLegIndex(base.minusSeconds(1)))
        assertNull(journey.currentLegIndex(at(22).scheduled))
    }

    @Test
    fun `a wait between legs counts for the next leg`() {
        assertEquals(0, journey.currentLegIndex(base))
        assertEquals(0, journey.currentLegIndex(at(5).scheduled.minusSeconds(1)))
        assertEquals(1, journey.currentLegIndex(at(5).scheduled))
        assertEquals(1, journey.currentLegIndex(at(8).scheduled))
    }

    @Test
    fun `a late leg stays current until its live arrival`() {
        assertEquals(1, journey.currentLegIndex(at(21).scheduled))
    }
}
