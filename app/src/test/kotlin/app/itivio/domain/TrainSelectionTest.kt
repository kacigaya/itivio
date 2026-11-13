package app.itivio.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TrainSelectionTest {
    private val base = Instant.parse("2026-11-13T15:30:00Z")
    private fun at(minutes: Long) = StopTime(base.plusSeconds(minutes * 60))
    private fun stop(id: String, station: String = id, departure: Long? = null, arrival: Long? = null) =
        Stop(id, GeoPoint(48.0, 2.0), id, arrival = arrival?.let(::at), departure = departure?.let(::at), stationId = station)
    private val line = Line("A", id = "rer-a")
    private val from = stop("a1", "a", departure = 20)
    private val to = stop("b1", "b", arrival = 33)
    private val ride = Leg.Transit(TransitMode.RAIL, from, to, at(20), at(33), emptyList(), line, "Boissy", "original", emptyList())
    private val walk = Leg.Street(TransitMode.WALK, stop("start"), from, at(18), at(20), emptyList(), 90.0)
    private val transfer = Leg.Street(TransitMode.WALK, to, stop("b2", "b"), at(33), at(35), emptyList(), 100.0)
    private val onward = ride.copy(from = stop("b2", "b"), to = stop("c"), start = at(37), end = at(64), tripId = "gamo")
    private val journey = Journey("j", listOf(walk, ride, transfer, onward), at(18), at(64), 1)
    private val departure = Departure(from, line, TransitMode.RAIL, "Chessy", at(4), false, "qiki22", tripCode = "QIKI22")
    private val trip = Trip(listOf(stop("a2", "a", departure = 4), stop("middle", arrival = 10), stop("b3", "b", arrival = 17)))

    @Test fun `switch confirms platforms and updates ride identity stops and walks`() {
        val updated = requireNotNull(journey.withDeparture(1, departure, trip))
        val selected = updated.legs[1] as Leg.Transit
        assertEquals("qiki22", selected.tripId)
        assertEquals("QIKI22", selected.tripCode)
        assertEquals("Chessy", selected.headsign)
        assertEquals("a2", selected.from.stopId)
        assertEquals("b3", selected.to.stopId)
        assertEquals(listOf("middle"), selected.intermediateStops.map { it.name })
        assertEquals(at(2), updated.departure)
        assertEquals(at(17), updated.legs[2].start)
        assertEquals(at(19), updated.legs[2].end)
        assertEquals("a2", updated.legs[0].to.stopId)
        assertEquals("b3", updated.legs[2].from.stopId)
        assertEquals(onward, updated.legs[3])
        assertNull(updated.missedConnectionIndex)
        assertEquals("original", (journey.legs[1] as Leg.Transit).tripId)
    }

    @Test fun `initial walk ends at the chosen departure even when original plan had a wait`() {
        val earlyWalk = walk.copy(start = at(0), end = at(2))
        val waiting = journey.copy(legs = listOf(earlyWalk, ride, transfer, onward), departure = at(0))
        val updated = requireNotNull(waiting.withDeparture(1, departure, trip))
        assertEquals(at(2), updated.departure)
        assertEquals(at(4), updated.legs[0].end)
    }

    @Test fun `changed platforms discard walking directions for the old platforms`() {
        val geometry = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.001, 2.001))
        val routedWalk = walk.copy(geometry = geometry, steps = listOf(WalkStep(90.0, "Old platform", geometry)))
        val original = journey.copy(legs = listOf(routedWalk, ride, transfer.copy(geometry = geometry), onward))
        val updated = requireNotNull(original.withDeparture(1, departure, trip))
        assertTrue(updated.legs[0].geometry.isEmpty())
        assertTrue((updated.legs[0] as Leg.Street).steps.isEmpty())
        assertTrue(updated.legs[2].geometry.isEmpty())
    }

    @Test fun `late alternative leaves onward train intact and reports missed connection`() {
        val late = departure.copy(time = at(30))
        val lateTrip = Trip(listOf(stop("a2", "a", departure = 30), stop("b3", "b", arrival = 45)))
        val updated = requireNotNull(journey.withDeparture(1, late, lateTrip))
        assertEquals(3, updated.missedConnectionIndex)
        assertEquals(onward, updated.legs[3])
    }

    @Test fun `loop trips use the exact boarding visit`() {
        val loop = Trip(listOf(stop("a2", "a", departure = 1), stop("b3", "b", arrival = 3)) + trip.stops)
        val updated = requireNotNull(journey.withDeparture(1, departure, loop))
        assertEquals(at(4), updated.legs[1].start)
        assertEquals(at(17), updated.legs[1].end)
    }

    @Test fun `invalid cancelled and wrong line alternatives never replace a ride`() {
        assertNull(journey.withDeparture(1, departure.copy(cancelled = true), trip))
        assertNull(journey.withDeparture(1, departure, trip.copy(cancelled = true)))
        assertNull(journey.withDeparture(1, departure.copy(line = Line("B", id = "other")), trip))
        assertNull(journey.withDeparture(1, departure.copy(stop = stop("other")), trip))
        assertNull(journey.withDeparture(1, departure, Trip(trip.stops.take(1))))
        assertNull(journey.withDeparture(1, departure, Trip(trip.stops.map { it.copy(cancelled = true) })))
        assertNull(journey.withDeparture(1, departure.copy(time = at(99)), trip))
        assertNull(journey.withDeparture(0, departure, trip))
    }
}
