package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.ui.results.TravelTimeDialog
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class TravelTimeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `large text keeps confirmation visible and usable`() {
        val time = LocalDate.now().atTime(12, 34).atZone(ZoneId.systemDefault()).toInstant()
        var confirmed: Pair<Instant?, Boolean>? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                ItivioTheme(dynamicColor = false) {
                    TravelTimeDialog(time, true, onDismiss = {}, onConfirm = { picked, arrive -> confirmed = picked to arrive })
                }
            }
        }
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(time to true, confirmed) }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `short window scrolls the picker without hiding confirmation`() {
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                TravelTimeDialog(null, false, onDismiss = {}, onConfirm = { _, _ -> })
            }
        }
        compose.onNodeWithContentDescription("Select hour").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

}
