package app.itivio.ui

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.domain.LocationKind
import app.itivio.domain.SavedPlaces
import app.itivio.ui.common.UserLocation
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.SearchViewModel
import app.itivio.ui.theme.ItivioTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocationSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `suggestions with blank and repeated IDs remain selectable`() {
        val locations = listOf("First address", "Second address", "Third address", "Fourth address").mapIndexed { index, name ->
            FakeRoutingRepository.location(name).copy(
                id = if (index < 2) "" else "my-location",
                kind = LocationKind.ADDRESS,
            )
        }
        val repository = FakeRoutingRepository().apply { searchResult = { locations } }
        val viewModel = SearchViewModel(repository, UserLocation())
        val places = FakePlacesRepository()
        val placesViewModel = SavedPlacesViewModel(places)
        var picked: Endpoint? = null

        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                val state = rememberSearchBarState()
                val text = rememberTextFieldState("59 Rue de la République")
                LaunchedEffect(Unit) { state.animateToExpanded() }
                LocationSearchView(
                    state, text, "Destination", offerMyLocation = true, onPick = { picked = it },
                    viewModel = viewModel, places = placesViewModel,
                )
            }
        }

        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            repository.searches.isNotEmpty()
        }
        locations.forEach { compose.onNodeWithText(it.name).assertIsDisplayed() }
        compose.onNodeWithText(locations[1].name).performClick()
        compose.runOnIdle {
            assertEquals(Endpoint.Place(locations[1]), picked)
            assertEquals(listOf(locations[1]), places.saved.value.recents)
        }
    }

    @Test
    fun `an empty query shows saved places and can clear recent searches`() {
        val home = FakeRoutingRepository.location("Rue de Rivoli 12")
        val favorite = FakeRoutingRepository.location("Alexanderplatz", stopId = "s1")
        val recent = FakeRoutingRepository.location("Tempelhofer Feld")
        val places = FakePlacesRepository(SavedPlaces(home = home, favorites = listOf(favorite), recents = listOf(recent)))
        var picked: Endpoint? = null
        showSearch(places) { picked = it }

        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText(home.name).assertIsDisplayed()
        compose.onNodeWithText(favorite.name).assertIsDisplayed()
        compose.onNodeWithText(recent.name).assertIsDisplayed()

        compose.onNodeWithContentDescription("Clear recent searches").performClick()
        compose.onNodeWithText(recent.name).assertDoesNotExist()
        compose.onNodeWithText(favorite.name).performClick()
        compose.runOnIdle {
            assertEquals(Endpoint.Place(favorite), picked)
            // Saved places are not search history.
            assertTrue(places.saved.value.recents.isEmpty())
        }
    }

    @Test
    fun `the place menu adds a favorite and sets home`() {
        val recent = FakeRoutingRepository.location("Tempelhofer Feld")
        val places = FakePlacesRepository(SavedPlaces(recents = listOf(recent)))
        showSearch(places) {}

        compose.onNodeWithContentDescription("More options for ${recent.name}").performClick()
        compose.onNodeWithText("Add to favorites").performClick()
        // The place is now in the favorites and the recent searches; both rows have a menu.
        compose.onAllNodesWithContentDescription("More options for ${recent.name}")[0].performClick()
        compose.onNodeWithText("Set as home").performClick()

        compose.runOnIdle {
            assertEquals(listOf(recent), places.saved.value.favorites)
            assertEquals(recent, places.saved.value.home)
        }
    }

    private fun showSearch(places: FakePlacesRepository, onPick: (Endpoint) -> Unit) {
        val viewModel = SearchViewModel(FakeRoutingRepository(), UserLocation())
        val placesViewModel = SavedPlacesViewModel(places)
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                val state = rememberSearchBarState()
                val text = rememberTextFieldState()
                LaunchedEffect(Unit) { state.animateToExpanded() }
                LocationSearchView(
                    state, text, "Destination", offerMyLocation = false, onPick = onPick,
                    viewModel = viewModel, places = placesViewModel,
                )
            }
        }
        compose.waitForIdle()
    }
}
