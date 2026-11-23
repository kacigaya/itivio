package app.itivio.ui

import androidx.lifecycle.SavedStateHandle
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
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.planner.RouteSort
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val userLocation = UserLocation()
    private val options = FakeJourneyOptionsRepository()
    private val viewModel by lazy { PlannerViewModel(repository, userLocation, options, SavedStateHandle()) }

    @Test
    fun `my location without a fix fails without calling the service`() = runTest(main.dispatcher) {
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(LoadState.Failed(Failure.NO_LOCATION), viewModel.state.value.results)
        assertTrue(repository.plans.isEmpty())
    }

    @Test
    fun `an answer that is ready at once skips the loading state`() = runTest(main.dispatcher) {
        userLocation.point.value = GeoPoint(52.52, 13.405)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        // As a cached plan: the repository answers with no wait, so no skeleton frame shows.
        val seen = mutableListOf<LoadState<*>>()
        val watcher = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect { seen += it.results } }
        viewModel.swap()
        assertEquals(2, repository.plans.size)
        assertTrue(viewModel.state.value.results is LoadState.Ready)
        assertTrue(seen.none { it == LoadState.Loading })
        watcher.cancel()
    }

    @Test
    fun `plans from the current position once a destination is set`() = runTest(main.dispatcher) {
        userLocation.point.value = GeoPoint(52.52, 13.405)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        assertEquals(LoadState.Loading, viewModel.state.value.results)
        advanceUntilIdle()

        val request = repository.plans.single()
        assertEquals(GeoPoint(52.52, 13.405), request.from.point)
        assertEquals("Zoo", request.to.name)
        assertEquals(1, viewModel.state.value.journeys.size)
    }

    @Test
    fun `a new destination cancels the request in flight`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<JourneyPage>()
        repository.planResult = { request ->
            if (request.to.name == "Slow") gate.await() else JourneyPage(listOf(journey("fast", 10, 0, 0.0)))
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Slow")))
        advanceUntilIdle()
        viewModel.setDestination(Endpoint.Place(location("Fast")))
        advanceUntilIdle()
        gate.complete(JourneyPage(listOf(journey("stale", 99, 0, 0.0))))
        advanceUntilIdle()

        assertEquals(listOf("fast"), viewModel.state.value.journeys.map { it.id })
    }

    @Test
    fun `service errors map to failure states`() = runTest(main.dispatcher) {
        repository.planResult = { throw RoutingException.Offline() }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.state.value.results)

        repository.planResult = { throw RoutingException.Server(503) }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(LoadState.Failed(Failure.SERVER), viewModel.state.value.results)
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
        assertEquals(stored, repository.plans.single().options)
    }

    @Test
    fun `the first search uses the stored options`() = runTest(main.dispatcher) {
        val stored = JourneyOptions(excludedModes = setOf(TransitMode.BUS), wheelchair = true)
        options.stored.value = stored
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(stored, repository.plans.single().options)
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
        assertEquals(changed, repository.plans.last().options)
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
        assertEquals(changed, repository.plans.single().options)
    }

    @Test
    fun `options changed in the settings apply to the next search`() = runTest(main.dispatcher) {
        // The planner reads the stored options when it starts.
        assertEquals(JourneyOptions(), viewModel.state.value.options)
        advanceUntilIdle()
        val changed = JourneyOptions(wheelchair = true)

        // The settings screen writes to the store, not to the planner.
        options.save(changed)
        advanceUntilIdle()
        assertEquals(changed, viewModel.state.value.options)

        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(changed, repository.plans.single().options)
    }

    @Test
    fun `a storage failure keeps planning with the options of the session`() = runTest(main.dispatcher) {
        options.failing = true
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        assertEquals(JourneyOptions(), repository.plans.single().options)
        assertTrue(viewModel.state.value.optionsLoaded)

        val changed = JourneyOptions(wheelchair = true)
        viewModel.setOptions(changed)
        advanceUntilIdle()

        assertEquals(changed, repository.plans.last().options)
        assertEquals(1, viewModel.state.value.journeys.size)
    }

    @Test
    fun `sorting orders by the chosen criterion`() = runTest(main.dispatcher) {
        repository.planResult = {
            JourneyPage(
                listOf(journey("slow", 40, 0, 100.0, "U1"), journey("fast", 20, 2, 900.0, "U2"), journey("mid", 30, 1, 50.0, "U3")),
            )
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        viewModel.setSort(RouteSort.FASTEST)
        assertEquals(listOf("fast", "mid", "slow"), viewModel.state.value.routes.map { it.single().id })
        viewModel.setSort(RouteSort.FEWEST_TRANSFERS)
        assertEquals(listOf("slow", "mid", "fast"), viewModel.state.value.routes.map { it.single().id })
        viewModel.setSort(RouteSort.LEAST_WALKING)
        assertEquals(listOf("mid", "slow", "fast"), viewModel.state.value.routes.map { it.single().id })
        assertEquals("mid", viewModel.state.value.highlighted?.id)
    }

    @Test
    fun `journeys with the same lines and boarding stops share one route`() = runTest(main.dispatcher) {
        repository.planResult = {
            JourneyPage(
                listOf(
                    journey("late", 20, 0, 0.0, "U2", startMinute = 10),
                    journey("other", 30, 0, 0.0, "S1", startMinute = 2),
                    journey("early", 25, 0, 0.0, "U2", startMinute = 5),
                ),
            )
        }
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        assertEquals(listOf(listOf("early", "late"), listOf("other")), viewModel.state.value.routes.map { route -> route.map { it.id } })
        assertEquals("early", viewModel.state.value.highlighted?.id)
        viewModel.setSort(RouteSort.FASTEST)
        assertEquals(listOf("early", "other"), viewModel.state.value.routes.map { it.first().id })
    }

    @Test
    fun `swap exchanges endpoints and replans`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        viewModel.swap()
        advanceUntilIdle()

        val request = repository.plans.last()
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
        val request = repository.plans.last()
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
        val request = repository.plans.last()
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

    /** A new handle with the same values, as Android gives one after process death. */
    private fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun `the request survives process death and plans again`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        val time = Instant.parse("2026-11-09T18:00:00Z")
        PlannerViewModel(repository, userLocation, options, handle).apply {
            setOrigin(Endpoint.Place(location("Home")))
            setTime(time, arriveBy = true)
            setDestination(Endpoint.Place(location("Zoo", stopId = "zoo")))
        }
        advanceUntilIdle()

        val restored = PlannerViewModel(repository, userLocation, options, handle.afterProcessDeath())
        assertEquals(LoadState.Loading, restored.state.value.results)
        advanceUntilIdle()

        val state = restored.state.value
        assertEquals(Endpoint.Place(location("Home")), state.origin)
        assertEquals(Endpoint.Place(location("Zoo", stopId = "zoo")), state.destination)
        assertEquals(time, state.time)
        assertTrue(state.arriveBy)
        assertEquals(2, repository.plans.size)
        assertEquals(repository.plans[0], repository.plans[1])
        assertEquals(1, state.journeys.size)
    }

    @Test
    fun `a restored session from my location waits for the first fix`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        userLocation.point.value = GeoPoint(52.52, 13.405)
        PlannerViewModel(repository, userLocation, options, handle).setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()

        val fresh = UserLocation()
        val restored = PlannerViewModel(repository, fresh, options, handle.afterProcessDeath())
        // Less than the wait for the fix.
        advanceTimeBy(5_000)
        assertEquals(1, repository.plans.size)
        assertEquals(LoadState.Loading, restored.state.value.results)

        fresh.point.value = GeoPoint(52.5, 13.4)
        advanceUntilIdle()
        assertEquals(GeoPoint(52.5, 13.4), repository.plans.last().from.point)
        assertEquals(1, restored.state.value.journeys.size)
    }

    @Test
    fun `leaving the results forgets the request`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        PlannerViewModel(repository, userLocation, options, handle).apply {
            setDestination(Endpoint.Place(location("Zoo")))
            reset()
        }
        advanceUntilIdle()

        val restored = PlannerViewModel(repository, userLocation, options, handle.afterProcessDeath())
        advanceUntilIdle()
        assertEquals(null, restored.state.value.destination)
        assertEquals(LoadState.Idle, restored.state.value.results)
    }

    @Test
    fun `a new route from here leaves now from the device position`() = runTest(main.dispatcher) {
        viewModel.setOrigin(Endpoint.Place(location("Home")))
        viewModel.setTime(Instant.parse("2026-11-09T18:00:00Z"), arriveBy = true)
        viewModel.setDestination(Endpoint.Place(location("Zoo")))
        advanceUntilIdle()
        userLocation.point.value = GeoPoint(52.51, 13.39)

        viewModel.replanFromHere()
        advanceUntilIdle()

        val request = repository.plans.last()
        assertEquals(GeoPoint(52.51, 13.39), request.from.point)
        assertEquals("Zoo", request.to.name)
        assertEquals(null, request.time)
        assertFalse(request.arriveBy)
        assertEquals(Endpoint.MyLocation, viewModel.state.value.origin)
    }
}
