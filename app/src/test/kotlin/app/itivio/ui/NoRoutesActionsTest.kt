package app.itivio.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.itivio.ui.planner.Endpoint
import app.itivio.domain.JourneyOptions
import app.itivio.ui.common.LoadState
import app.itivio.ui.planner.PlannerState
import app.itivio.ui.results.ResultsScreen
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import app.itivio.domain.PlacesRepository
import app.itivio.ui.common.UserLocation
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.SearchViewModel
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class NoRoutesActionsTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun koin() {
        startKoin {
            modules(module {
                single { UserLocation() }
                single<app.itivio.domain.RoutingRepository> { FakeRoutingRepository() }
                single<PlacesRepository> { FakePlacesRepository() }
                viewModelOf(::SearchViewModel)
                viewModelOf(::SavedPlacesViewModel)
            })
        }
    }

    @After
    fun stop() = stopKoin()

    private fun show(options: JourneyOptions, onOptions: (JourneyOptions) -> Unit = {}) = compose.setContent {
        ItivioTheme(dynamicColor = false) {
            ResultsScreen(
                wide = false,
                now = Instant.EPOCH,
                state = PlannerState(
                    optionsLoaded = true,
                    destination = Endpoint.Place(FakeRoutingRepository.location("Potsdamer Platz")),
                    options = options,
                    results = LoadState.Ready(emptyList()),
                ),
                onBack = {}, onOrigin = {}, onDestination = {}, onSwap = {}, onTime = { _, _ -> }, onOptions = onOptions, onSort = {},
                onSelect = {}, onOpen = {}, onRetry = {},
            )
        }
    }

    @Test
    fun `no routes offers to reset changed options`() {
        var applied: JourneyOptions? = null
        show(JourneyOptions(maxTransfers = 0)) { applied = it }
        compose.onNodeWithText("Reset journey options").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(JourneyOptions(), applied) }
    }

    @Test
    fun `no routes with default options offers only another time`() {
        show(JourneyOptions())
        compose.onNodeWithText("Change the time").assertExists()
        compose.onNodeWithText("Reset journey options").assertDoesNotExist()
    }
}
