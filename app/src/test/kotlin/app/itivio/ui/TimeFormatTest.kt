package app.itivio.ui

import android.app.Application
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.itivio.ui.common.formatTime
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS", application = Application::class)
class TimeFormatTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `clock follows setting changes and preserves stop time zone`() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.System.putString(resolver, Settings.System.TIME_12_24, "24")
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                Text(formatTime(Instant.parse("2026-11-10T16:30:00Z"), ZoneId.of("Europe/Berlin")))
            }
        }
        compose.onNodeWithText("17:30").assertIsDisplayed()

        Settings.System.putString(resolver, Settings.System.TIME_12_24, "12")
        shadowOf(Looper.getMainLooper()).idle()
        // ICU uses a narrow no-break space before the day period in this locale.
        compose.onNodeWithText("5:30", substring = true).assertIsDisplayed()
        compose.onNodeWithText("PM", substring = true).assertIsDisplayed()

        Settings.System.putString(resolver, Settings.System.TIME_12_24, "24")
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText("17:30").assertIsDisplayed()
    }
}
