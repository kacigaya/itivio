package app.itivio.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.itivio.domain.JourneyOptions
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkingSpeed
import app.itivio.ui.settings.optionsSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsSummaryTest {
    @get:Rule val compose = createComposeRule()

    private fun assertSummary(options: JourneyOptions, expected: String) {
        compose.setContent { Text(optionsSummary(options)) }
        compose.onNodeWithText(expected).assertExists()
    }

    @Test fun `default options name the defaults`() =
        assertSummary(JourneyOptions(), "All modes, no transfer limit, normal walking speed")

    @Test fun `changed options list only the changes`() = assertSummary(
        JourneyOptions(excludedModes = setOf(TransitMode.COACH, TransitMode.FERRY), maxTransfers = 1, walkingSpeed = WalkingSpeed.FAST),
        "2 modes off · At most 1 transfer · Fast walking",
    )

    @Test fun `wheelchair replaces the walking speed`() = assertSummary(
        JourneyOptions(maxTransfers = 0, wheelchair = true, walkingSpeed = WalkingSpeed.SLOW),
        "Direct only · Wheelchair accessible",
    )
}
