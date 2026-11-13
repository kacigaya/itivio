package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.RoutingException
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.domain.Trip
import app.itivio.ui.itinerary.TripTrackerViewModel
import app.itivio.ui.itinerary.TripTrackerViewModel.Companion.REFRESH_MILLIS
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripTrackerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val base = Instant.parse("2026-11-09T10:00:00Z")
    private var now = base
    private val viewModel by lazy { TripTrackerViewModel(repository) { now } }

    private fun at(minutes: Long, live: Long? = null) = StopTime(base.plusSeconds(minutes * 60), live?.let { base.plusSeconds(it * 60) })
    private fun stop(id: String, arrival: StopTime? = null, departure: StopTime? = null) =
        Stop(id, GeoPoint(52.5, 13.4), stopId = id, arrival = arrival, departure = departure)
    private fun ride(trip: String, from: String, to: String, start: Long, end: Long) = Leg.Transit(
        TransitMode.BUS, stop(from, departure = at(start)), stop(to, arrival = at(end)), at(start), at(end),
        emptyList(), Line("M41"), "Town hall", trip, emptyList(),
    )
    private val journey = Journey(
        "j",
        listOf(ride("t1", "A", "B", 5, 15), ride("t2", "B", "C", 20, 30)),
        at(5), at(30), transfers = 1,
    )
    private fun liveTrip(from: String, to: String, start: Long, end: Long, delay: Long) =
        Trip(listOf(stop(from, departure = at(start, start + delay)), stop(to, arrival = at(end, end + delay))))

    @Test
    fun `applies the live times of each trip and refreshes once a minute`() = runTest(main.dispatcher) {
        repository.tripResult = { id -> if (id == "t1") liveTrip("A", "B", 5, 15, 2) else liveTrip("B", "C", 20, 30, 4) }
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(journey)
        runCurrent()

        assertEquals(listOf("t1", "t2"), repository.tripCalls)
        assertEquals(at(15, 17), viewModel.journey.value!!.legs[0].end)
        assertEquals(at(30, 34), viewModel.journey.value!!.arrival)

        advanceTimeBy(REFRESH_MILLIS - 1)
        runCurrent()
        assertEquals(2, repository.tripCalls.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(4, repository.tripCalls.size)
    }

    @Test
    fun `a failed request keeps the last times and an ended trip is not asked again`() = runTest(main.dispatcher) {
        repository.tripResult = { liveTrip("A", "B", 5, 15, 2) }
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(journey)
        runCurrent()
        val first = viewModel.journey.value!!

        repository.tripResult = { throw RoutingException.Offline() }
        now = at(25).scheduled
        advanceTimeBy(REFRESH_MILLIS)
        runCurrent()

        assertEquals(first, viewModel.journey.value)
        // The first trip ended at 10:17; at 10:25 only the second one is asked.
        assertEquals(listOf("t1", "t2", "t2"), repository.tripCalls)
    }
}
