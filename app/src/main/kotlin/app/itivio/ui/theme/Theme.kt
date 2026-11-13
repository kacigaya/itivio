package app.itivio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import app.itivio.ui.common.LocalTimeFormatter
import app.itivio.ui.common.rememberTimeFormatter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.itivio.R

// Fallback schemes for devices without dynamic color (API < 31). Generated with
// material-color-utilities (tonal spot, 2021 spec) from seed #1B6B5A.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0C6B59),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA1F2DB),
    onPrimaryContainer = Color(0xFF005142),
    inversePrimary = Color(0xFF85D6BF),
    secondary = Color(0xFF4B635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9DE),
    onSecondaryContainer = Color(0xFF334C44),
    tertiary = Color(0xFF426277),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC6E7FF),
    onTertiaryContainer = Color(0xFF294A5E),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceTint = Color(0xFF0C6B59),
    inverseSurface = Color(0xFF2B322F),
    inverseOnSurface = Color(0xFFECF2EE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FBF7),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD5DBD8),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF85D6BF),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFA1F2DB),
    inversePrimary = Color(0xFF0C6B59),
    secondary = Color(0xFFB2CCC3),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF334C44),
    onSecondaryContainer = Color(0xFFCDE9DE),
    tertiary = Color(0xFFA9CBE3),
    onTertiary = Color(0xFF0F3447),
    tertiaryContainer = Color(0xFF294A5E),
    onTertiaryContainer = Color(0xFFC6E7FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceTint = Color(0xFF85D6BF),
    inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2B322F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B38),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceDim = Color(0xFF0E1513),
)

/**
 * Google Sans Flex with round terminals. One variable font file gives all weights. The UI pairs
 * a heavy weight for numbers and names with a light weight for units and secondary text.
 */
private val GoogleSansFlex = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
        .map { weight ->
            Font(
                R.font.google_sans_flex,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("ROND", 100f)),
            )
        },
)

/**
 * True when the app shows its dark theme. The user can set the theme in the app, so this can
 * differ from the setting of the device. The map reads it to pick its style.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val ItivioTypography = Typography(fontFamily = GoogleSansFlex)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItivioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = ItivioTypography,
        content = {
            CompositionLocalProvider(
                LocalTimeFormatter provides rememberTimeFormatter(),
                LocalDarkTheme provides darkTheme,
                content = content,
            )
        },
    )
}
