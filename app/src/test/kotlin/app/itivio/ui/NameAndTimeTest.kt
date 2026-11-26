package app.itivio.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.itivio.ui.itinerary.NameAndTime
import app.itivio.ui.theme.ItivioTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NameAndTimeTest {
    @get:Rule val compose = createComposeRule()

    private val longName = "S+U Alexanderplatz (U2) (U5) (U8) Alexanderplatz/Memhardstraße"

    /** The stop row of the itinerary: a rail as tall as its content, beside the name and time. */
    private fun stopRow(name: String, width: Int) {
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                Row(Modifier.width(width.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().semantics { contentDescription = "rail" })
                    NameAndTime(
                        name = { Text(name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) },
                        time = { Text("12:34", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) },
                    )
                }
            }
        }
    }

    @Test
    fun `a long name keeps the full width and puts the time below it`() {
        stopRow(longName, width = 200)
        val name = compose.onNodeWithText(longName).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("12:34").fetchSemanticsNode().boundsInRoot

        // The time moved below the name instead of squeezing the name against it.
        assertTrue("The time is not below the name: name=$name time=$time", time.top >= name.bottom - 0.5f)
        // The name keeps the full row width: it is not measured against the time beside it.
        assertTrue("The name is squeezed next to the time: name=$name time=$time", name.right >= time.right - 0.5f)
    }

    @Test
    fun `the intrinsically sized rail covers the time stacked below the name`() {
        stopRow(longName, width = 200)
        val rail = compose.onNodeWithContentDescription("rail").fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("12:34").fetchSemanticsNode().boundsInRoot

        // The rail height is the stacked height, so the time cannot overdraw the next row.
        assertTrue("The rail is shorter than the content: rail=$rail time=$time", rail.bottom >= time.bottom - 0.5f)
    }

    @Test
    fun `a short name keeps the time on its line`() {
        stopRow("S+U Alexanderplatz", width = 320)
        val name = compose.onNodeWithText("S+U Alexanderplatz").fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("12:34").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "The time is not beside the name: name=$name time=$time",
            abs(time.top - name.top) < 1f && time.left > name.right,
        )
    }
}
