package app.itivio.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.itivio.domain.Alert
import app.itivio.domain.Disruption
import app.itivio.ui.disruptions.DisruptionGroups
import app.itivio.ui.common.LoadState
import app.itivio.ui.disruptions.disruptions
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompactDisruptionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a compact row opens into the full card and closes again`() {
        val alert = Alert("Line 6 closed", "Replacement buses run between the stations.")
        val groups = DisruptionGroups(saved = listOf(Disruption(alert, listOf("Nation"), emptyList())))
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                LazyColumn { disruptions(LoadState.Ready(groups), Instant.EPOCH, onRetry = {}, compact = true) }
            }
        }
        compose.onNodeWithText("Replacement buses run between the stations.").assertDoesNotExist()
        compose.onNodeWithText("Line 6 closed").performClick()
        compose.onNodeWithText("Replacement buses run between the stations.").assertExists()
        compose.onNodeWithText("Show less").performClick()
        compose.onNodeWithText("Replacement buses run between the stations.").assertDoesNotExist()
    }
}
