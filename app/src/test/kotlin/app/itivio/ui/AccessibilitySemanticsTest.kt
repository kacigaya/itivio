package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import app.itivio.ui.common.LocalTimeFormatter
import java.time.format.DateTimeFormatter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import app.itivio.domain.StopTime
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.TimeText
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS", application = Application::class)
class AccessibilitySemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a delayed time is announced once with both live and scheduled values`() {
        val scheduled = Instant.parse("2026-11-09T10:00:00Z")
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTimeFormatter provides DateTimeFormatter.ofPattern("HH:mm")) {
                    TimeText(StopTime(scheduled, scheduled.plusSeconds(120)), ZoneId.of("UTC"))
                }
            }
        }
        compose.onNodeWithContentDescription("10:02, Live, Scheduled 10:00").assertIsDisplayed()
        // The semantic label replaces visible child text instead of repeating both times.
        val descriptions = compose.onAllNodesWithText("10:02")
        descriptions.assertCountEquals(0)
    }

    @Test
    fun `loading has one accessible label`() {
        compose.setContent { ItivioTheme(dynamicColor = false) { LoadingState() } }
        compose.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }
}
