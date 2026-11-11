package app.itivio.ui

import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.RoutingException
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.itinerary.LineTimesViewModel
import app.itivio.ui.itinerary.LineTimesViewModel.Companion.REFRESH_MILLIS
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LineTimesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val base = Instant.parse("2026-11-09T10:00:00Z")
    private var now = base
    // Lazy: stateIn reads Dispatchers.Main after the rule installs it.
    private val viewModel by lazy { LineTimesViewModel(repository) { now } }

    private fun at(minutes: Long) = StopTime(base.plusSeconds(minutes * 60))
    private fun stop(id: String?) = Stop("Stop", GeoPoint(52.5, 13.4), stopId = id)
    private fun ride(stopId: String?, start: Long) = Leg.Transit(
        TransitMode.BUS, stop(stopId), stop("end"), at(start), at(start + 10), emptyList(), Line("M41"), "Town hall", "trip", emptyList(),
    )
    private fun journey(vararg legs: Leg) = Journey("j", legs.toList(), legs.first().start, legs.last().end, transfers = 0)
    private fun departure(minutes: Long) =
        Departure(stop("a"), Line("M41"), TransitMode.BUS, "Town hall", at(minutes), cancelled = false, tripId = "trip-$minutes")

    @Test
    fun `loads times by leg index for transit legs that have a stop ID`() = runTest(main.dispatcher) {
        val walk = Leg.Street(TransitMode.WALK, stop(null), stop("a"), at(0), at(5), emptyList(), 300.0)
        repository.lineResult = { stopId -> if (stopId == "a") listOf(departure(8)) else emptyList() }
        viewModel.times.launchIn(backgroundScope)
        viewModel.show(journey(walk, ride("a", start = 8), ride(null, start = 20)))
        runCurrent()

        assertEquals(mapOf(1 to listOf(departure(8))), viewModel.times.value)
        assertEquals(listOf("a"), repository.lineCalls.map { it.first })
    }

    @Test
    fun `the list starts at the planned departure, then at the current time`() = runTest(main.dispatcher) {
        viewModel.times.launchIn(backgroundScope)
        viewModel.show(journey(ride("a", start = 8)))
        runCurrent()
        assertEquals(at(8).scheduled, repository.lineCalls.single().second)

        now = at(30).scheduled
        advanceTimeBy(REFRESH_MILLIS)
        runCurrent()
        assertEquals(now, repository.lineCalls.last().second)
    }

    @Test
    fun `refreshes only after the interval and keeps the last times when a refresh fails`() = runTest(main.dispatcher) {
        repository.lineResult = { listOf(departure(8)) }
        viewModel.times.launchIn(backgroundScope)
        viewModel.show(journey(ride("a", start = 8)))
        runCurrent()

        advanceTimeBy(REFRESH_MILLIS - 1)
        runCurrent()
        assertEquals(1, repository.lineCalls.size)

        repository.lineResult = { throw RoutingException.Offline() }
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.lineCalls.size)
        assertEquals(mapOf(0 to listOf(departure(8))), viewModel.times.value)
    }

    @Test
    fun `a journey without transit stops makes no request`() = runTest(main.dispatcher) {
        viewModel.times.launchIn(backgroundScope)
        viewModel.show(journey(Leg.Street(TransitMode.WALK, stop(null), stop(null), at(0), at(5), emptyList(), 300.0)))
        runCurrent()
        advanceTimeBy(REFRESH_MILLIS * 2)
        runCurrent()

        assertTrue(viewModel.times.value.isEmpty())
        assertTrue(repository.lineCalls.isEmpty())
    }

    @Test
    fun `requests stop after the last collector leaves and a new collector keeps the times`() = runTest(main.dispatcher) {
        repository.lineResult = { listOf(departure(8)) }
        val collector = viewModel.times.launchIn(backgroundScope)
        viewModel.show(journey(ride("a", start = 8)))
        runCurrent()

        collector.cancel()
        runCurrent()
        advanceTimeBy(5_000 + REFRESH_MILLIS * 3)
        runCurrent()
        assertEquals(1, repository.lineCalls.size)

        repository.lineResult = { throw RoutingException.Offline() }
        viewModel.times.launchIn(backgroundScope)
        runCurrent()
        assertEquals(2, repository.lineCalls.size)
        assertEquals(mapOf(0 to listOf(departure(8))), viewModel.times.value)
    }
}
