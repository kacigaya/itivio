package app.itivio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.itivio.domain.SettingsRepository
import app.itivio.domain.ThemeMode
import app.itivio.ui.ItivioRoot
import app.itivio.ui.theme.ItivioTheme
import kotlinx.coroutines.flow.catch
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings: SettingsRepository = koinInject()
            // A storage failure, such as a full disk, leaves the theme of the device.
            val stored by remember(settings) { settings.theme.catch { emit(ThemeMode.SYSTEM) } }
                .collectAsStateWithLifecycle(initialValue = null)
            // The first frame waits for the stored theme, so the app does not flash the wrong one.
            val mode = stored ?: return@setContent
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The icons of the system bars follow the theme of the app, not that of the device.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ItivioTheme(darkTheme = dark) {
                ItivioRoot()
            }
        }
    }
}
