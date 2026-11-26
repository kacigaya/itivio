package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.ui.settings.AboutScreen
import app.itivio.ui.theme.ItivioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `what's new opens the release of this version`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                ItivioTheme(dynamicColor = false) { AboutScreen(version = "0.14.2", onBack = {}) }
            }
        }
        compose.onNodeWithText("What’s new").performClick()
        compose.runOnIdle { assertEquals(listOf("https://github.com/kacigaya/itivio/releases/tag/v0.14.2"), opened) }
    }
}
