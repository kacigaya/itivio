package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.itivio.domain.JourneyOptions
import app.itivio.domain.ThemeMode
import app.itivio.ui.results.JourneyOptionsDialog
import app.itivio.ui.settings.SettingsScreen
import app.itivio.ui.theme.ItivioTheme
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp", application = Application::class)
class LargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun useLargeText() = RuntimeEnvironment.setFontScale(2f)

    @After
    fun restoreTextSize() = RuntimeEnvironment.setFontScale(1f)

    @Test
    fun `theme choices remain readable and selectable at 200 percent`() {
        var selected by mutableStateOf(ThemeMode.SYSTEM)
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    SettingsScreen(selected, JourneyOptions(), {}, { selected = it }, {}, {}, {}, {})
                }
            }
        }
        for (label in listOf("System", "Light", "Dark")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Clipped theme label: $label size=${layouts.single().size} width=${layouts.single().didOverflowWidth} height=${layouts.single().didOverflowHeight} lines=${layouts.single().lineCount}", layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithText("Dark").performClick().assertIsSelected()
    }

    @Test
    fun `journey option actions stay visible at 200 percent`() {
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    JourneyOptionsDialog(JourneyOptions(), onDismiss = {}, onConfirm = {})
                }
            }
        }
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsDisplayed()
        compose.onNodeWithText("Fast").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }
}
