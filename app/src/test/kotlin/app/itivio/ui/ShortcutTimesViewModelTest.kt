package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyPage
import app.itivio.domain.RoutingException
import app.itivio.domain.SavedPlaces
import app.itivio.domain.SettingsRepository
import app.itivio.domain.Shortcut
import app.itivio.domain.ThemeMode
import app.itivio.domain.TransitMode
import app.itivio.ui.common.UserLocation
import app.itivio.ui.explore.ShortcutTimesViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutTimesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeSettings(on: Boolean) : SettingsRepository {
        override val theme = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setTheme(mode: ThemeMode) { theme.value = mode }
        override val shortcutTimes = MutableStateFlow(on)
        override suspend fun setShortcutTimes(on: Boolean) { shortcutTimes.value = on }
    }

    private val repository = FakeRoutingRepository()
    private val userLocation = UserLocation()
    private val places = FakePlacesRepository(
        SavedPlaces(home = FakeRoutingRepository.location("Home street"), work = FakeRoutingRepository.location("Office")),
    )
    private val options = FakeJourneyOptionsRepository()
    // The fake journeys leave at the base time; the clock is 5 minutes before it.
    private val clock = Clock.fixed(FakeRoutingRepository.base.minusSeconds(300), ZoneOffset.UTC)

    /** The time on the monotonic clock of the view model, in milliseconds. */
    private var elapsed = 0L

    private fun viewModel(on: Boolean = true, clock: Clock = this.clock) =
        ShortcutTimesViewModel(repository, userLocation, places, options, FakeSettings(on), clock) { elapsed }

    private fun TestScope.collectShortcutTimes(viewModel: ShortcutTimesViewModel): Job =
        viewModel.times.launchIn(backgroundScope)

    @Test
    fun `off by default sends nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel(on = false)
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertTrue(viewModel.times.value.isEmpty())
        assertTrue(repository.plans.isEmpty())
    }

    @Test
    fun `on plans to each shortcut and counts from now to the first arrival`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        // The fake journey takes 20 minutes, and the rider waits 5 for it.
        assertEquals(mapOf(Shortcut.HOME to Duration.ofMinutes(25), Shortcut.WORK to Duration.ofMinutes(25)), viewModel.times.value)
        assertEquals(listOf("Home street", "Office"), repository.plans.map { it.to.name })
    }

    @Test
    fun `a move re-plans only after the minimum interval`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()
        assertEquals(2, repository.plans.size)

        // 200 m further inside the interval: the buttons keep their times and the app waits.
        userLocation.point.value = GeoPoint(52.5218, 13.405)
        runCurrent()
        assertEquals(2, repository.plans.size)
        assertEquals(Duration.ofMinutes(25), viewModel.times.value[Shortcut.HOME])

        // The next move after the interval plans both journeys again.
        elapsed = ShortcutTimesViewModel.MIN_INTERVAL.toMillis()
        userLocation.point.value = GeoPoint(52.5236, 13.405)
        runCurrent()
        assertEquals(4, repository.plans.size)
        assertEquals(Duration.ofMinutes(25), viewModel.times.value[Shortcut.HOME])
    }

    @Test
    fun `a new place plans at once`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()
        assertEquals(2, repository.plans.size)

        // The rider sets Work while home is open: the interval does not hold the new place back.
        places.saved.value = places.saved.value.copy(work = FakeRoutingRepository.location("Office 2"))
        runCurrent()

        assertEquals(listOf("Home street", "Office", "Home street", "Office 2"), repository.plans.map { it.to.name })
    }

    @Test
    fun `a cancelled request publishes nothing, not even the other shortcut`() = runTest(main.dispatcher) {
        // Work is cancelled with the screen closing, while Home still answers. A swallowed
        // cancellation would publish the half result.
        repository.planResult = { request ->
            if (request.to.name == "Office") throw CancellationException("the screen closed")
            JourneyPage(listOf(FakeRoutingRepository.journey("a", minutes = 20, transfers = 1, walk = 300.0)))
        }
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertEquals(2, repository.plans.size)
        assertTrue(viewModel.times.value.isEmpty())
    }

    @Test
    fun `a failed request leaves out its shortcut`() = runTest(main.dispatcher) {
        repository.planResult = { request ->
            if (request.to.name == "Office") throw RoutingException.Server(503)
            JourneyPage(listOf(FakeRoutingRepository.journey("a", minutes = 20, transfers = 1, walk = 300.0)))
        }
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertEquals(mapOf(Shortcut.HOME to Duration.ofMinutes(25)), viewModel.times.value)
    }

    @Test
    fun `a time under a minute shows nothing`() = runTest(main.dispatcher) {
        // The rider stands at Home: the journey arrives in 30 seconds.
        val near = Clock.fixed(FakeRoutingRepository.base.plusSeconds(20 * 60 - 30), ZoneOffset.UTC)
        val viewModel = viewModel(clock = near)
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertEquals(2, repository.plans.size)
        assertTrue(viewModel.times.value.isEmpty())
    }

    @Test
    fun `the plan carries the journey options`() = runTest(main.dispatcher) {
        val chosen = JourneyOptions(excludedModes = setOf(TransitMode.BUS), maxTransfers = 1)
        options.stored.value = chosen
        val viewModel = viewModel()
        collectShortcutTimes(viewModel)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertEquals(listOf(chosen, chosen), repository.plans.map { it.options })
    }
}
