package app.itivio.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class JourneyTripTest {
    private val base = Instant.parse("2026-11-09T10:00:00Z")
    private fun at(minutes: Long, live: Long? = null) = StopTime(base.plusSeconds(minutes * 60), live?.let { base.plusSeconds(it * 60) })
    private fun stop(id: String, arrival: StopTime? = null, departure: StopTime? = null) =
        Stop(id, GeoPoint(52.5, 13.4), stopId = id, arrival = arrival, departure = departure)

    // Walk 0-5, bus B→D 8-20, walk 20-25.
    private val journey = Journey(
        id = "j",
        legs = listOf(
            Leg.Street(TransitMode.WALK, stop("A"), stop("B"), at(0), at(5), emptyList(), 300.0),
            Leg.Transit(
                TransitMode.BUS, stop("B", departure = at(8)), stop("D", arrival = at(20)), at(8), at(20),
                emptyList(), Line("M41"), "Town hall", "trip", listOf(stop("C")),
            ),
            Leg.Street(TransitMode.WALK, stop("D"), stop("E"), at(20), at(25), emptyList(), 300.0),
        ),
        departure = at(0),
        arrival = at(25),
        transfers = 0,
    )

    @Test
    fun `a trip update moves the leg and the walk after it`() {
        val trip = Trip(
            listOf(
                stop("A0", departure = at(2)),
                stop("B", arrival = at(7, 9), departure = at(8, 10)),
                stop("C", arrival = at(14, 16), departure = at(14, 16)),
                stop("D", arrival = at(20, 23)),
                stop("F", arrival = at(30, 33)),
            ),
            alerts = listOf(Alert("Diversion", "Road works")),
        )
        val updated = journey.withTrip(1, trip)
        val bus = updated.legs[1] as Leg.Transit
        assertEquals(at(8, 10), bus.start)
        assertEquals(at(20, 23), bus.end)
        assertEquals(listOf("C"), bus.intermediateStops.map { it.name })
        assertEquals(listOf(Alert("Diversion", "Road works")), bus.alerts)
        // The last walk starts when the bus arrives; the first walk keeps its times.
        assertEquals(base.plusSeconds(23 * 60), updated.legs[2].start.best)
        assertEquals(base.plusSeconds(28 * 60), updated.arrival.best)
        assertEquals(at(0), updated.legs[0].start)
        assertEquals(at(0), updated.departure)
    }

    @Test
    fun `a trip without the stops of the leg changes nothing`() {
        val other = Trip(listOf(stop("X", departure = at(8)), stop("Y", arrival = at(20))))
        assertEquals(journey, journey.withTrip(1, other))
        assertEquals(journey, journey.withTrip(0, other))
    }

    @Test
    fun `a cancelled trip marks the leg`() {
        val trip = Trip(listOf(stop("B", departure = at(8)), stop("D", arrival = at(20))), cancelled = true)
        assertTrue(journey.withTrip(1, trip).isCancelled)
    }

    @Test
    fun `a loop trip boards at the visit with the planned time`() {
        val trip = Trip(
            listOf(
                stop("B", departure = at(2, 2)),
                stop("D", arrival = at(4, 4), departure = at(4, 4)),
                stop("B", arrival = at(7), departure = at(8, 9)),
                stop("D", arrival = at(20, 21)),
            ),
        )
        val bus = journey.withTrip(1, trip).legs[1]
        assertEquals(at(8, 9), bus.start)
        assertEquals(at(20, 21), bus.end)
    }
}
