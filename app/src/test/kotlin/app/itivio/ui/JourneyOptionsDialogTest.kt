package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.itivio.domain.JourneyOptions
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkingSpeed
import app.itivio.ui.results.JourneyOptionsDialog
import app.itivio.ui.theme.ItivioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class JourneyOptionsDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmed: JourneyOptions? = null

    private fun show(options: JourneyOptions = JourneyOptions(), fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ItivioTheme(dynamicColor = false) {
                    JourneyOptionsDialog(options, onDismiss = {}, onConfirm = { confirmed = it })
                }
            }
        }
    }

    @Test
    fun `done returns all changes at one time`() {
        show()
        compose.onNodeWithText("Bus").performScrollTo().performClick()
        compose.onNodeWithText("1 transfer").performScrollTo().performClick()
        compose.onNodeWithText("Fast").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, confirmed) }

        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle {
            assertEquals(
                JourneyOptions(excludedModes = setOf(TransitMode.BUS), maxTransfers = 1, walkingSpeed = WalkingSpeed.FAST),
                confirmed,
            )
        }
    }

    @Test
    fun `the last selected mode stays on and the dialog says why`() {
        val onlyTram = JourneyOptions(excludedModes = (JourneyOptions.SELECTABLE_MODES - TransitMode.TRAM).toSet())
        show(onlyTram)
        compose.onNodeWithText("One mode must stay selected.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Tram").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle { assertEquals(onlyTram, confirmed) }
    }

    @Test
    fun `wheelchair routing turns off the walking speed`() {
        show(JourneyOptions(walkingSpeed = WalkingSpeed.FAST))
        compose.onNodeWithText("Wheelchair accessible").performScrollTo().performClick()

        compose.onNodeWithText("Fast").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Not used for wheelchair-accessible routes.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reset goes back to the defaults`() {
        show(JourneyOptions(excludedModes = setOf(TransitMode.FERRY), maxTransfers = 2, wheelchair = true))

        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle { assertEquals(JourneyOptions(), confirmed) }
    }

    @Test
    fun `large text keeps confirmation visible and usable`() {
        show(fontScale = 2f)
        compose.onNodeWithText("Fast").performScrollTo().performClick()

        compose.onNodeWithText("Done").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals(JourneyOptions(walkingSpeed = WalkingSpeed.FAST), confirmed) }
    }
}
