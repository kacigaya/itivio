package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyPage
import app.itivio.domain.RoutingException
import app.itivio.ui.FakeRoutingRepository.Companion.journey
import app.itivio.ui.FakeRoutingRepository.Companion.location
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.planner.RouteSort
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val userLocation = UserLocation()
    private val viewModel = PlannerViewModel(repository, userLocation)

    @Test
    fun `my location without a fix fails without calling the service`() = runTest(main.dispatcher) {
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(LoadState.Failed(Failure.NO_LOCATION), viewModel.state.value.results)
        assertTrue(repository.plans.isEmpty())
    }

    @Test
    fun `plans from the current position once a destination is set`() = runTest(main.dispatcher) {
        userLocation.point.value = GeoPoint(52.52, 13.405)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        assertEquals(LoadState.Loading, viewModel.state.value.results)
        advanceUntilIdle()

        val (request, cursor) = repository.plans.single()
        assertEquals(GeoPoint(52.52, 13.405), request.from.point)
        assertEquals("Zoo", request.to.name)
        assertEquals(null, cursor)
        assertEquals(1, viewModel.state.value.journeys.size)
        assertTrue(viewModel.state.value.canLoadLater)
    }

    @Test
    fun `a new destination cancels the request in flight`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<JourneyPage>()
        repository.planResult = { request, _ ->
            if (request.to.name == "Slow") gate.await() else JourneyPage(listOf(journey("fast", 10, 0, 0.0)), null, null)
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Slow")))
        advanceUntilIdle()
        viewModel.setDestination(Endpoint.Place(location("Fast")))
        advanceUntilIdle()
        gate.complete(JourneyPage(listOf(journey("stale", 99, 0, 0.0)), null, null))
        advanceUntilIdle()

        assertEquals(listOf("fast"), viewModel.state.value.journeys.map { it.id })
    }

    @Test
    fun `service errors map to failure states`() = runTest(main.dispatcher) {
        repository.planResult = { _, _ -> throw RoutingException.Offline() }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.state.value.results)

        repository.planResult = { _, _ -> throw RoutingException.Server(503) }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(LoadState.Failed(Failure.SERVER), viewModel.state.value.results)
    }

    @Test
    fun `later page appends new journeys without duplicates`() = runTest(main.dispatcher) {
        repository.planResult = { _, cursor ->
            if (cursor == null) JourneyPage(listOf(journey("a", 20, 0, 0.0)), null, "c1")
            else JourneyPage(listOf(journey("a", 20, 0, 0.0), journey("b", 25, 0, 0.0)), null, null)
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        viewModel.loadLater()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.state.value.journeys.map { it.id })
        assertEquals("c1", repository.plans.last().second)
        assertFalse(viewModel.state.value.canLoadLater)
    }

    @Test
    fun `sorting orders by the chosen criterion`() = runTest(main.dispatcher) {
        repository.planResult = { _, _ ->
            JourneyPage(
                listOf(journey("slow", 40, 0, 100.0), journey("fast", 20, 2, 900.0), journey("mid", 30, 1, 50.0)),
                null,
                null,
            )
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        viewModel.setSort(RouteSort.FASTEST)
        assertEquals(listOf("fast", "mid", "slow"), viewModel.state.value.sortedJourneys.map { it.id })
        viewModel.setSort(RouteSort.FEWEST_TRANSFERS)
        assertEquals(listOf("slow", "mid", "fast"), viewModel.state.value.sortedJourneys.map { it.id })
        viewModel.setSort(RouteSort.LEAST_WALKING)
        assertEquals(listOf("mid", "slow", "fast"), viewModel.state.value.sortedJourneys.map { it.id })
        assertEquals("mid", viewModel.state.value.highlighted?.id)
    }

    @Test
    fun `swap exchanges endpoints and replans`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        viewModel.swap()
        advanceUntilIdle()

        val request = repository.plans.last().first
        assertEquals("Zoo", request.from.name)
        assertEquals("Home", request.to.name)
    }

    @Test
    fun `setting an arrival time replans with that time`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        val time = Instant.parse("2026-11-09T12:00:00Z")

        viewModel.setTime(time, arriveBy = true)
        advanceUntilIdle()

        assertEquals(2, repository.plans.size)
        val request = repository.plans.last().first
        assertEquals(time, request.time)
        assertTrue(request.arriveBy)
        assertEquals(time, viewModel.state.value.time)
        assertTrue(viewModel.state.value.arriveBy)
    }

    @Test
    fun `clearing the time replans for departure now`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setTime(Instant.parse("2026-11-09T12:00:00Z"), arriveBy = true)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        viewModel.setTime(null, arriveBy = true)
        advanceUntilIdle()

        assertEquals(2, repository.plans.size)
        val request = repository.plans.last().first
        assertEquals(null, request.time)
        assertFalse(request.arriveBy)
        assertEquals(null, viewModel.state.value.time)
        assertFalse(viewModel.state.value.arriveBy)
    }

    @Test
    fun `reset clears the time and arrival mode`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setTime(Instant.parse("2026-11-09T12:00:00Z"), arriveBy = true)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.arriveBy)

        viewModel.reset()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.time)
        assertFalse(viewModel.state.value.arriveBy)
        assertEquals(1, repository.plans.size)
    }
}
