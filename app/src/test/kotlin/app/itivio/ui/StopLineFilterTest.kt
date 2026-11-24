package app.itivio.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.common.LoadState
import app.itivio.ui.stop.StopScreen
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class StopLineFilterTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-24T08:00:00Z")
    private val stop = Stop("Alexanderplatz", GeoPoint(52.52, 13.41), stopId = "s")

    @Test
    fun `a line chip shows only that line and a second tap shows all again`() {
        val departures = listOf(
            Departure(stop, Line("U2"), TransitMode.SUBWAY, "Ruhleben", StopTime(now.plusSeconds(180)), false, "a"),
            Departure(stop, Line("S5"), TransitMode.SUBURBAN, "Strausberg Nord", StopTime(now.plusSeconds(420)), false, "b"),
        )
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                StopScreen(
                    wide = true, name = "Alexanderplatz", departures = LoadState.Ready(departures), now = now, favorite = false,
                    onBack = {}, onDirections = {}, onToggleFavorite = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Line U2").performScrollTo().performClick()
        compose.onNodeWithText("Strausberg Nord").assertDoesNotExist()
        compose.onNodeWithText("Ruhleben").assertExists()
        compose.onNodeWithContentDescription("Line U2").performClick()
        compose.onNodeWithText("Strausberg Nord").assertExists()
    }
}
