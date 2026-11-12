package app.itivio.ui

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.itivio.ui.common.MapPanel
import app.itivio.ui.theme.ItivioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class MapPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `gesture navigation area uses sheet color in light theme`() = checkNavigationBar(24, dark = false)

    @Test
    fun `three button navigation area uses sheet color in dark theme`() = checkNavigationBar(48, dark = true)

    private fun checkNavigationBar(insetDp: Int, dark: Boolean) {
        lateinit var view: View
        var bottomInset = 0
        var sheetColor = Color.Unspecified
        compose.setContent {
            val localView = LocalView.current
            val inset = with(LocalDensity.current) { insetDp.dp.roundToPx() }
            ItivioTheme(darkTheme = dark, dynamicColor = false) {
                val surface = MaterialTheme.colorScheme.surfaceContainerLow
                SideEffect {
                    view = localView
                    bottomInset = inset
                    sheetColor = surface
                }
                Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                    MapPanel(wide = false, peekHeight = 200.dp) {
                        Box(Modifier.height(1000.dp))
                    }
                }
            }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(
                view.rootView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottomInset))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                    .build(),
            )
        }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = pixels.width / 2
        assertEquals("Map stays visible above the sheet", Color.Magenta, pixels[x, 10])
        assertEquals("Sheet reaches the navigation area", sheetColor, pixels[x, pixels.height - bottomInset / 2])
        assertEquals("No map strip at the bottom edge", sheetColor, pixels[x, pixels.height - 1])

        compose.onRoot().performTouchInput {
            swipeUp(startY = height - bottomInset - 100f, endY = height * 0.1f)
        }
        val expanded = compose.onRoot().captureToImage().toPixelMap()
        assertEquals("Sheet expands over the map", sheetColor, expanded[x, expanded.height / 2])
        assertEquals("Expanded sheet covers the navigation area", sheetColor, expanded[x, expanded.height - bottomInset / 2])
    }
}
