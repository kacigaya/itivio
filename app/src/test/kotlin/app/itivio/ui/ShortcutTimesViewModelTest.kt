package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.SavedPlaces
import app.itivio.domain.SettingsRepository
import app.itivio.domain.Shortcut
import app.itivio.domain.ThemeMode
import app.itivio.ui.common.UserLocation
import app.itivio.ui.explore.ShortcutTimesViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
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
    // The fake journeys leave at the base time; the clock is 5 minutes before it.
    private val clock = Clock.fixed(FakeRoutingRepository.base.minusSeconds(300), ZoneOffset.UTC)

    private fun viewModel(on: Boolean) =
        ShortcutTimesViewModel(repository, userLocation, places, FakeJourneyOptionsRepository(), FakeSettings(on), clock)

    @Test
    fun `off by default sends nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel(on = false)
        viewModel.times.launchIn(backgroundScope)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        assertTrue(viewModel.times.value.isEmpty())
        assertTrue(repository.plans.isEmpty())
    }

    @Test
    fun `on plans to each shortcut and counts from now to the first arrival`() = runTest(main.dispatcher) {
        val viewModel = viewModel(on = true)
        viewModel.times.launchIn(backgroundScope)
        userLocation.point.value = GeoPoint(52.52, 13.405)
        runCurrent()

        // The fake journey takes 20 minutes, and the rider waits 5 for it.
        assertEquals(mapOf(Shortcut.HOME to Duration.ofMinutes(25), Shortcut.WORK to Duration.ofMinutes(25)), viewModel.times.value)
        assertEquals(listOf("Home street", "Office"), repository.plans.map { it.to.name })
    }
}
