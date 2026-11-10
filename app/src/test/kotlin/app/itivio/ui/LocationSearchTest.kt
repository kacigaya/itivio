package app.itivio.ui

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.domain.LocationKind
import app.itivio.ui.common.UserLocation
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.SearchViewModel
import app.itivio.ui.theme.ItivioTheme
import java.time.Duration
import org.junit.Assert.assertEquals
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
        var picked: Endpoint? = null

        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                val state = rememberSearchBarState()
                val text = rememberTextFieldState("59 Rue de la République")
                LaunchedEffect(Unit) { state.animateToExpanded() }
                LocationSearchView(state, text, "Destination", offerMyLocation = true, onPick = { picked = it }, viewModel = viewModel)
            }
        }

        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            repository.searches.isNotEmpty()
        }
        locations.forEach { compose.onNodeWithText(it.name).assertIsDisplayed() }
        compose.onNodeWithText(locations[1].name).performClick()
        compose.runOnIdle { assertEquals(Endpoint.Place(locations[1]), picked) }
    }
}
