package app.itivio.ui

import app.itivio.domain.*
import app.itivio.ui.itinerary.TripTrackerViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainSelectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeRoutingRepository()
    private val base = Instant.parse("2026-11-13T15:30:00Z")
    private fun at(minutes: Long) = StopTime(base.plusSeconds(minutes * 60))
    private fun stop(id: String, departure: Long? = null, arrival: Long? = null) =
        Stop(id, GeoPoint(48.0, 2.0), id, departure = departure?.let(::at), arrival = arrival?.let(::at))
    private val line = Line("A", id = "rer-a")
    private val ride = Leg.Transit(TransitMode.RAIL, stop("a", departure = 20), stop("b", arrival = 33), at(20), at(33),
        emptyList(), line, "Boissy", "original", emptyList())
    private val plan = Journey("j", listOf(ride), at(20), at(33), 0)
    private val departure = Departure(stop("a"), line, TransitMode.RAIL, "Chessy", at(4), false, "alternative", tripCode = "QIKI22")
    private fun trip(start: Long, end: Long) = Trip(listOf(stop("a", departure = start), stop("b", arrival = end)))
    private val viewModel by lazy { TripTrackerViewModel(repository) { base } }

    @Test fun `selection updates tracker identity and reset restores original train`() = runTest(main.dispatcher) {
        repository.tripResult = { if (it == "alternative") trip(4, 17) else trip(20, 33) }
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        viewModel.selectDeparture(0, departure)
        runCurrent()
        assertEquals("alternative", (viewModel.journey.value!!.legs[0] as Leg.Transit).tripId)
        assertEquals(at(17), viewModel.journey.value!!.arrival)
        assertEquals(setOf(0), viewModel.selection.value.changedLegs)
        assertNull(viewModel.selection.value.loadingLeg)
        repository.tripCalls.clear()
        advanceTimeBy(TripTrackerViewModel.REFRESH_MILLIS)
        runCurrent()
        assertEquals(listOf("alternative"), repository.tripCalls)
        viewModel.resetTrain(0)
        runCurrent()
        assertEquals("original", (viewModel.journey.value!!.legs[0] as Leg.Transit).tripId)
        assertTrue(viewModel.selection.value.changedLegs.isEmpty())
    }

    @Test fun `reset restores walking directions when original platforms are confirmed`() = runTest(main.dispatcher) {
        val geometry = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.001, 2.001))
        val from = ride.from.copy(stationId = "station-a")
        val walk = Leg.Street(TransitMode.WALK, stop("origin"), from, at(18), at(20), geometry, 90.0)
        val original = plan.copy(legs = listOf(walk, ride.copy(from = from)), departure = at(18))
        repository.tripResult = { id ->
            if (id == "alternative") Trip(listOf(stop("other-platform", departure = 4).copy(stationId = "station-a"), stop("b", arrival = 17)))
            else Trip(listOf(from, stop("b", arrival = 33)))
        }
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(original)
        runCurrent()
        viewModel.selectDeparture(1, departure.copy(stop = from))
        runCurrent()
        assertTrue(viewModel.journey.value!!.legs[0].geometry.isEmpty())
        assertEquals(setOf(1), viewModel.selection.value.platformChangedLegs)
        viewModel.resetTrain(1)
        runCurrent()
        assertEquals(geometry, viewModel.journey.value!!.legs[0].geometry)
        assertTrue(viewModel.selection.value.platformChangedLegs.isEmpty())
    }

    @Test fun `network failure and incompatible trip keep the current train`() = runTest(main.dispatcher) {
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        repository.tripResult = { throw RoutingException.Offline() }
        viewModel.selectDeparture(0, departure)
        runCurrent()
        assertEquals(plan, viewModel.journey.value)
        assertEquals(0, viewModel.selection.value.failedLeg)
        repository.tripResult = { Trip(emptyList()) }
        viewModel.selectDeparture(0, departure)
        runCurrent()
        assertEquals(plan, viewModel.journey.value)
        assertEquals(0, viewModel.selection.value.failedLeg)
        assertNull(viewModel.selection.value.loadingLeg)
    }

    @Test fun `cancelled departures and missing trip IDs cannot be selected`() = runTest(main.dispatcher) {
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        repository.tripCalls.clear()
        viewModel.selectDeparture(0, departure.copy(cancelled = true))
        viewModel.selectDeparture(0, departure.copy(tripId = null))
        runCurrent()
        assertTrue(repository.tripCalls.isEmpty())
        assertTrue(viewModel.selection.value.changedLegs.isEmpty())
    }

    @Test fun `latest selection cancels a pending switch`() = runTest(main.dispatcher) {
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        val pending = CompletableDeferred<Trip>()
        repository.tripResult = { if (it == "alternative") pending.await() else trip(6, 19) }
        viewModel.selectDeparture(0, departure)
        runCurrent()
        assertEquals(0, viewModel.selection.value.loadingLeg)
        viewModel.selectDeparture(0, departure.copy(tripId = "newest", time = at(6)))
        runCurrent()
        pending.complete(trip(4, 17))
        runCurrent()
        assertEquals("newest", (viewModel.journey.value!!.legs[0] as Leg.Transit).tripId)
    }

    @Test fun `showing a different plan cancels selection and clears overrides`() = runTest(main.dispatcher) {
        viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        val pending = CompletableDeferred<Trip>()
        repository.tripResult = { if (it == "alternative") pending.await() else Trip(emptyList()) }
        viewModel.selectDeparture(0, departure)
        runCurrent()
        val other = plan.copy(id = "other")
        viewModel.show(other)
        runCurrent()
        pending.complete(trip(4, 17))
        runCurrent()
        assertEquals(other, viewModel.journey.value)
        assertNull(viewModel.selection.value.loadingLeg)
        assertTrue(viewModel.selection.value.changedLegs.isEmpty())
    }

    @Test fun `returning from background keeps the selected train`() = runTest(main.dispatcher) {
        repository.tripResult = { if (it == "alternative") trip(4, 17) else trip(20, 33) }
        val collector = viewModel.journey.launchIn(backgroundScope)
        viewModel.show(plan)
        runCurrent()
        viewModel.selectDeparture(0, departure)
        runCurrent()
        collector.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        viewModel.show(plan)
        viewModel.journey.launchIn(backgroundScope)
        runCurrent()
        assertEquals("alternative", (viewModel.journey.value!!.legs[0] as Leg.Transit).tripId)
        assertEquals(setOf(0), viewModel.selection.value.changedLegs)
    }
}
