package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyPage
import app.itivio.domain.RoutingException
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkingSpeed
import app.itivio.ui.FakeRoutingRepository.Companion.journey
import app.itivio.ui.FakeRoutingRepository.Companion.location
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.Paging
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
    private val options = FakeJourneyOptionsRepository()
    private val viewModel by lazy { PlannerViewModel(repository, userLocation, options) }

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
    fun `earlier page puts new journeys first and keeps the shown journey`() = runTest(main.dispatcher) {
        repository.planResult = { _, cursor ->
            when (cursor) {
                null -> JourneyPage(listOf(journey("b", 20, 0, 0.0)), "e1", "l1")
                "e1" -> JourneyPage(listOf(journey("a", 25, 0, 0.0), journey("b", 20, 0, 0.0)), null, "l0")
                else -> JourneyPage(listOf(journey("c", 30, 0, 0.0)), "e2", null)
            }
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canLoadEarlier)

        viewModel.loadEarlier()
        assertEquals(Paging.EARLIER, viewModel.state.value.loadingPage)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.state.value.journeys.map { it.id })
        assertEquals("b", viewModel.state.value.highlighted?.id)
        assertEquals("e1", repository.plans.last().second)
        assertFalse(viewModel.state.value.canLoadEarlier)
        assertEquals(null, viewModel.state.value.loadingPage)

        // The earlier page does not change where the later pages continue.
        viewModel.loadLater()
        advanceUntilIdle()
        assertEquals("l1", repository.plans.last().second)
        assertEquals(listOf("a", "b", "c"), viewModel.state.value.journeys.map { it.id })
    }

    @Test
    fun `only one page loads at a time`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<JourneyPage>()
        repository.planResult = { _, cursor ->
            if (cursor == null) JourneyPage(listOf(journey("b", 20, 0, 0.0)), "e1", "l1") else gate.await()
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        viewModel.loadEarlier()
        viewModel.loadLater()
        advanceUntilIdle()

        assertEquals(listOf(null, "e1"), repository.plans.map { it.second })
    }

    @Test
    fun `a new search drops a page in flight`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<JourneyPage>()
        repository.planResult = { request, cursor ->
            when {
                cursor != null -> gate.await()
                else -> JourneyPage(listOf(journey(request.to.name, 20, 0, 0.0)), "e1", null)
            }
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        viewModel.loadEarlier()
        advanceUntilIdle()

        viewModel.setDestination(Endpoint.Place(location("Park")))
        advanceUntilIdle()
        gate.complete(JourneyPage(listOf(journey("stale", 99, 0, 0.0)), null, null))
        advanceUntilIdle()

        assertEquals(listOf("Park"), viewModel.state.value.journeys.map { it.id })
        assertEquals(null, viewModel.state.value.loadingPage)
    }

    @Test
    fun `a failed earlier page keeps the results and retries the same request`() = runTest(main.dispatcher) {
        var failPage = true
        repository.planResult = { _, cursor ->
            when {
                cursor == null -> JourneyPage(listOf(journey("b", 20, 0, 0.0)), "e1", "l1")
                failPage -> throw RoutingException.Offline()
                else -> JourneyPage(listOf(journey("a", 25, 0, 0.0)), null, "l0")
            }
        }
        val changed = JourneyOptions(maxTransfers = 0, wheelchair = true)
        viewModel.setOptions(changed)
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        val initialRequest = repository.plans.single().first

        viewModel.loadEarlier()
        advanceUntilIdle()

        assertEquals(listOf("b"), viewModel.state.value.journeys.map { it.id })
        assertEquals(null, viewModel.state.value.loadingPage)
        assertTrue(viewModel.state.value.canLoadEarlier)
        assertTrue(viewModel.state.value.canLoadLater)

        failPage = false
        viewModel.loadEarlier()
        advanceUntilIdle()

        assertEquals(listOf(null, "e1", "e1"), repository.plans.map { it.second })
        assertTrue(repository.plans.all { it.first == initialRequest })
        assertEquals(changed, repository.plans.last().first.options)
        assertEquals(listOf("a", "b"), viewModel.state.value.journeys.map { it.id })
    }

    @Test
    fun `options stay unavailable until the saved choices load`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        options.readGate = gate
        val stored = JourneyOptions(wheelchair = true)
        options.stored.value = stored
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.optionsLoaded)
        assertTrue(repository.plans.isEmpty())
        viewModel.reset()
        assertFalse(viewModel.state.value.optionsLoaded)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.optionsLoaded)
        assertEquals(stored, viewModel.state.value.options)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(stored, repository.plans.single().first.options)
    }

    @Test
    fun `the first search uses the stored options`() = runTest(main.dispatcher) {
        val stored = JourneyOptions(excludedModes = setOf(TransitMode.BUS), wheelchair = true)
        options.stored.value = stored
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(stored, repository.plans.single().first.options)
        assertEquals(stored, viewModel.state.value.options)
    }

    @Test
    fun `new options are stored and start a new search`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        val changed = JourneyOptions(maxTransfers = 1, walkingSpeed = WalkingSpeed.FAST)

        viewModel.setOptions(changed)
        advanceUntilIdle()
        viewModel.setOptions(changed)
        advanceUntilIdle()

        assertEquals(2, repository.plans.size)
        assertEquals(changed, repository.plans.last().first.options)
        assertEquals(changed, options.stored.value)
    }

    @Test
    fun `options set before the stored ones load are not replaced`() = runTest(main.dispatcher) {
        options.stored.value = JourneyOptions(wheelchair = true)
        val changed = JourneyOptions(maxTransfers = 0)

        viewModel.setOptions(changed)
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(changed, viewModel.state.value.options)
        assertEquals(changed, repository.plans.single().first.options)
    }

    @Test
    fun `a storage failure keeps planning with the options of the session`() = runTest(main.dispatcher) {
        options.failing = true
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(JourneyOptions(), repository.plans.single().first.options)
        assertTrue(viewModel.state.value.optionsLoaded)

        val changed = JourneyOptions(wheelchair = true)
        viewModel.setOptions(changed)
        advanceUntilIdle()

        assertEquals(changed, repository.plans.last().first.options)
        assertEquals(1, viewModel.state.value.journeys.size)
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
    fun `reset clears the time and arrival mode and keeps the options`() = runTest(main.dispatcher) {
        val changed = JourneyOptions(maxTransfers = 2)
        viewModel.setOptions(changed)
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setTime(Instant.parse("2026-11-09T12:00:00Z"), arriveBy = true)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.arriveBy)

        viewModel.reset()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.time)
        assertFalse(viewModel.state.value.arriveBy)
        assertEquals(changed, viewModel.state.value.options)
        assertEquals(1, repository.plans.size)
    }
}
