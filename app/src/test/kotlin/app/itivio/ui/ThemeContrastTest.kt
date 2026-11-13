package app.itivio.ui

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import app.itivio.ui.theme.DarkColors
import app.itivio.ui.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ThemeContrastTest {
    @Test
    fun `light dark and system dynamic palettes have readable text pairs`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf(LightColors, DarkColors, dynamicLightColorScheme(context), dynamicDarkColorScheme(context))
            .forEachIndexed { index, scheme ->
                scheme.textPairs().forEach { (name, pair) ->
                    val (text, background) = pair
                    val ratio = (maxOf(text.luminance(), background.luminance()) + 0.05f) /
                        (minOf(text.luminance(), background.luminance()) + 0.05f)
                    assertTrue("Palette $index $name contrast: $ratio", ratio >= 4.5f)
                }
            }
    }

    private fun ColorScheme.textPairs(): Map<String, Pair<Color, Color>> = mapOf(
        "primary" to (onPrimary to primary),
        "primary container" to (onPrimaryContainer to primaryContainer),
        "secondary" to (onSecondary to secondary),
        "secondary container" to (onSecondaryContainer to secondaryContainer),
        "tertiary" to (onTertiary to tertiary),
        "tertiary container" to (onTertiaryContainer to tertiaryContainer),
        "error" to (onError to error),
        "error container" to (onErrorContainer to errorContainer),
        "surface" to (onSurface to surface),
        "body on high surface" to (onSurfaceVariant to surfaceContainerHigh),
        "body on highest surface" to (onSurfaceVariant to surfaceContainerHighest),
        "live on high surface" to (tertiary to surfaceContainerHigh),
    )
}
