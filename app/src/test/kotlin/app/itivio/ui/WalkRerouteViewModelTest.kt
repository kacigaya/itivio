package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyPage
import app.itivio.domain.Leg
import app.itivio.domain.RoutingException
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.walk.WalkRerouteViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkRerouteViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private var elapsed = 1_000L
    private val viewModel by lazy { WalkRerouteViewModel(repository) { elapsed } }

    private val start = StopTime(Instant.parse("2026-11-14T21:00:00Z"))
    private val end = Stop("Gare", GeoPoint(48.85, 2.40), stopId = "gare")
    private val planned = Leg.Street(TransitMode.WALK, Stop("A", GeoPoint(48.84, 2.39)), end, start, start, emptyList(), 500.0)
    private val here = GeoPoint(48.845, 2.395)

    private fun walk(mode: TransitMode = TransitMode.WALK) =
        Leg.Street(mode, Stop("", here), end.copy(zone = null), start, start, listOf(here, end.point), 300.0)

    private fun page(vararg legs: List<Leg>) =
        JourneyPage(legs.mapIndexed { i, it -> Journey("j$i", it, start, start, 0) }, earlierCursor = null, laterCursor = null)

    @Test
    fun `plans a walk from the position to the end of the leg`() = runTest(main.dispatcher) {
        val transit = FakeRoutingRepository.journey("t", minutes = 10, transfers = 0, walk = 100.0).legs
        repository.planResult = { _, _ -> page(transit, listOf(walk(TransitMode.BIKE)), listOf(walk())) }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()

        val request = repository.plans.single().first
        assertEquals(here, request.from.point)
        assertEquals("gare", request.to.stopId)
        assertEquals(end.point, request.to.point)
        assertNull(request.time)
        assertEquals(WalkRerouteViewModel.Reroute("key", walk()), viewModel.reroute.value)
    }

    @Test
    fun `sends at most one request in each interval`() = runTest(main.dispatcher) {
        repository.planResult = { _, _ -> page(listOf(walk())) }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        elapsed += 10_000
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        assertEquals(1, repository.plans.size)

        elapsed += WalkRerouteViewModel.MIN_INTERVAL.toMillis()
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        assertEquals(2, repository.plans.size)
    }

    @Test
    fun `a cancelled request publishes nothing`() = runTest(main.dispatcher) {
        val response = CompletableDeferred<JourneyPage>()
        repository.planResult = { _, _ -> response.await() }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        viewModel.cancel()
        response.complete(page(listOf(walk())))
        advanceUntilIdle()
        assertNull(viewModel.reroute.value)
    }

    @Test
    fun `keeps the planned leg when the request fails or finds no usable walk`() = runTest(main.dispatcher) {
        repository.planResult = { _, _ -> throw RoutingException.Offline() }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        assertNull(viewModel.reroute.value)

        elapsed += WalkRerouteViewModel.MIN_INTERVAL.toMillis()
        repository.planResult = { _, _ -> page(listOf(walk(TransitMode.BIKE))) }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        assertNull(viewModel.reroute.value)

        elapsed += WalkRerouteViewModel.MIN_INTERVAL.toMillis()
        repository.planResult = { _, _ -> page(listOf(walk().copy(geometry = emptyList()))) }
        viewModel.reroute("key", planned, here, JourneyOptions())
        advanceUntilIdle()
        assertNull(viewModel.reroute.value)
    }
}
