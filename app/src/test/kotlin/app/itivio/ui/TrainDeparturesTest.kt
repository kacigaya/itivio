package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    private val second = first.copy(tripId = "nani", tripCode = "NANI26", time = StopTime(now.plusSeconds(1_200)))

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
        compose.onNodeWithText("NANI26", substring = true).assertIsSelected()
        compose.onNodeWithText("QIKI22", substring = true).performClick()
        compose.onNodeWithText("QIKI22", substring = true).assertIsSelected()
        assertEquals("qiki", selected)
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("NANI26", substring = true).assertIsSelected()
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
        compose.onNodeWithText("QIKI22", substring = true).assertIsNotEnabled()
        compose.runOnIdle { loading = true }
        compose.onNodeWithText("NANI26", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        compose.onNodeWithText("Updating train…").assertExists()
    }
}
