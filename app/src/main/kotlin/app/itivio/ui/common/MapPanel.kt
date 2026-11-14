package app.itivio.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/** Width of the side pane on medium and wider windows. */
val PaneWidth = 400.dp
private val PaneMargin = 16.dp

/** True when content sits in a side pane beside the map (600dp and wider). */
@Composable
fun isWideLayout(): Boolean =
    currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

/**
 * Height of the [MapPanel] header that floats over the map in compact windows, status bar included.
 * The screen on top sets it when its header is laid out. Null outside the app root, such as in tests.
 */
val LocalPanelHeaderHeight = compositionLocalOf<MutableState<Dp>?> { null }

/**
 * The map area a [MapPanel] covers, so the camera can keep routes visible. [navigation] is the
 * size of the navigation bar or rail of the app, or zero when the screen shows none. The bar
 * covers the system navigation area, so the two do not add up.
 */
@Composable
fun panelInsets(wide: Boolean, peekHeight: Dp, headerHeight: Dp, navigation: Dp = 0.dp): PaddingValues =
    if (wide) {
        PaddingValues(start = PaneWidth + PaneMargin * 2 + navigation)
    } else {
        val below = if (navigation > 0.dp) navigation else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        PaddingValues(top = headerHeight, bottom = peekHeight + below)
    }

/**
 * Screen content over the map. Compact windows get a bottom sheet that peeks at [peekHeight];
 * wider windows get a side pane so the map stays visible. [header] floats above the map in both;
 * the home screen has none, because its search bar sits on the panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPanel(
    wide: Boolean,
    peekHeight: Dp,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (wide) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(PaneWidth + PaneMargin * 2).fillMaxHeight().safeDrawingPadding().padding(PaneMargin),
        ) {
            header()
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(Modifier.padding(top = 8.dp)) { content() }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            val corner = screenCornerRadius()
            BottomSheetScaffold(
                modifier = Modifier.navigationBarsPadding(),
                sheetPeekHeight = peekHeight,
                // The top corners of the sheet have the radius of the screen corners.
                sheetShape = RoundedCornerShape(topStart = corner, topEnd = corner),
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                containerColor = Color.Transparent,
                sheetContent = {
                    content()
                },
                content = {
                    val headerHeight = LocalPanelHeaderHeight.current
                    val density = LocalDensity.current
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .onSizeChanged { headerHeight?.value = with(density) { it.height.toDp() } }
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { header() }
                    }
                },
            )
            // The scaffold keeps content above the system bar; cover the map in that inset. The
            // padding gives the height: it is zero when the navigation bar of the app took the inset.
            Spacer(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .navigationBarsPadding(),
            )
        }
    }
}
