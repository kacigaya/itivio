package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.domain.*
import app.itivio.ui.itinerary.TrainDepartures
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrainDeparturesTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-13T15:30:00Z")
    private val stop = Stop("La Défense", GeoPoint(48.0, 2.0), "a", track = "1")
    private val first = Departure(stop, Line("A"), TransitMode.RAIL, "Chessy", StopTime(now.plusSeconds(240)), false, "qiki", tripCode = "QIKI22")
    private val second = first.copy(tripId = "nani", tripCode = "NANI26", headsign = "Boissy", time = StopTime(now.plusSeconds(1_200)))

    @Test fun `departure rows switch selection and reset the train`() {
        var selected by mutableStateOf(second.tripId)
        var changed by mutableStateOf(false)
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                TrainDepartures(listOf(first, second), selected, now, loading = false, canSelect = true,
                    changed = changed, failed = false,
                    onSelect = { selected = it.tripId; changed = true },
                    onReset = { selected = second.tripId; changed = false })
            }
        }
        compose.onNodeWithText("Boissy").assertIsSelected()
        compose.onNodeWithText("Chessy").performClick()
        compose.onNodeWithText("Chessy").assertIsSelected()
        assertEquals("qiki", selected)
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Boissy").assertIsSelected()
    }

    @Test fun `a row shows the destination without the trip code, and names the selected train`() {
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                TrainDepartures(listOf(first, second), second.tripId, now, loading = false, canSelect = true,
                    changed = false, failed = false, onSelect = {}, onReset = {})
            }
        }
        compose.onAllNodesWithText("QIKI22", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Your train").assertCountEquals(1)
    }

    @Test fun `a late train shows its delay in words`() {
        val late = first.copy(time = StopTime(now.plusSeconds(240), now.plusSeconds(840)))
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                TrainDepartures(listOf(late, second), second.tripId, now, loading = false, canSelect = true,
                    changed = false, failed = false, onSelect = {}, onReset = {})
            }
        }
        compose.onNodeWithText("10 min late").assertExists()
    }

    @Test fun `cancelled and loading departures cannot be selected`() {
        var loading by mutableStateOf(false)
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                TrainDepartures(listOf(first.copy(cancelled = true), second), second.tripId, now,
                    loading = loading, canSelect = !loading, changed = true, failed = false,
                    onSelect = {}, onReset = {})
            }
        }
        compose.onNodeWithText("Chessy").assertIsNotEnabled()
        compose.runOnIdle { loading = true }
        compose.onNodeWithText("Boissy").assertIsNotEnabled()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        compose.onNodeWithText("Updating train…").assertExists()
    }
}
