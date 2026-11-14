package app.itivio.ui.common

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The radius of the `extraLarge` shape, for screens that give no corner radius. */
private val FallbackCornerRadius = 28.dp

/**
 * The corner radius of the screen, so the sheets and the back gesture card have the shape of the
 * device. Android 12 and later give it. Older versions, square screens, and tests get the radius
 * of the `extraLarge` shape.
 */
@Composable
fun screenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    return remember(view, density, configuration) {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        } else {
            0
        }
        if (px > 0) with(density) { px.toDp() } else FallbackCornerRadius
    }
}
