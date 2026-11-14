package app.itivio.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.disruptions.DisruptionGroups
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.SearchViewModel
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The back gesture on the Disruptions page of the home sheet of a compact window. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
class HomeBackGestureTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var back: OnBackPressedDispatcher

    @Before
    fun koin() {
        startKoin {
            modules(module {
                single { UserLocation() }
                single<RoutingRepository> { FakeRoutingRepository() }
                single<PlacesRepository> { FakePlacesRepository() }
                viewModelOf(::SearchViewModel)
                viewModelOf(::SavedPlacesViewModel)
            })
        }
    }

    @After
    fun stop() = stopKoin()

    @Test
    fun `completed gesture goes to the departures page`() {
        openDisruptions()
        startGesture()
        // The preview does not change the page.
        compose.onNodeWithText("Disruptions").assertIsSelected()

        compose.runOnIdle { back.onBackPressed() }

        compose.onNodeWithText("Departures").assertIsSelected()
    }

    @Test
    fun `cancelled gesture stays on the disruptions page and the next one goes back`() {
        openDisruptions()
        startGesture()

        compose.runOnIdle { back.dispatchOnBackCancelled() }

        compose.onNodeWithText("Disruptions").assertIsSelected()
        startGesture()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Departures").assertIsSelected()
    }

    private fun openDisruptions() {
        compose.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            ItivioTheme(dynamicColor = false) {
                ExploreScreen(
                    wide = false, locationGranted = false, nearby = LoadState.Idle, now = Instant.EPOCH,
                    disruptions = LoadState.Ready(DisruptionGroups()),
                    onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {}, onStop = {}, onSettings = {},
                )
            }
        }
        compose.onNodeWithText("Disruptions").performClick()
        compose.onNodeWithText("Disruptions").assertIsSelected()
    }

    /** Starts a gesture from the left edge and moves it to the middle of the screen. */
    private fun startGesture() = compose.runOnIdle {
        back.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
    }
}
