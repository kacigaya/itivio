package app.itivio.ui.results

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.itivio.ui.common.BackGestureEasing
import app.itivio.ui.common.BackMillis
import app.itivio.ui.common.CardExitEasing
import app.itivio.ui.common.PreviewDrop
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * A bottom sheet that changes how the results are planned. It comes up from the bottom edge and
 * a swipe down closes it. A back gesture moves the sheet as it moves a screen card: down by
 * [PreviewDrop] of the window height while the scrim fades, then off the bottom edge on release.
 * A button that closes the sheet calls `close`, which sends the sheet down as "Back" sends a
 * screen card down, then runs its action.
 * The fields scroll when the window is short or the text is large, so the title and the buttons
 * stay visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable FlowRowScope.(close: (then: () -> Unit) -> Unit) -> Unit,
    fieldsAlignment: Alignment.Horizontal = Alignment.Start,
    fieldsArrangement: Arrangement.Vertical = Arrangement.Top,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    // How far down the sheet is during a back gesture, as a part of the window height.
    val drop = remember { Animatable(0f) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val scrim = BottomSheetDefaults.ScrimColor
    var closing by remember { mutableStateOf(false) }
    val close: (() -> Unit) -> Unit = { then ->
        if (!closing) {
            closing = true
            scope.launch {
                drop.animateTo(1f, tween(BackMillis, easing = CardExitEasing))
                then()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.graphicsLayer { translationY = drop.value * windowHeight },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimColor = scrim.copy(alpha = scrim.alpha * (1f - drop.value / PreviewDrop).coerceAtLeast(0f)),
    ) {
        // Registered after the handler of the sheet, so it takes the gesture in place of the M3 scaling.
        PredictiveBackHandler(enabled = !closing) { gesture ->
            var release = 0f
            try {
                gesture.collect {
                    release = it.progress
                    drop.snapTo(PreviewDrop * BackGestureEasing.transform(it.progress))
                }
                drop.animateTo(1f, tween(((1f - release) * BackMillis).toInt(), easing = CardExitEasing))
                onDismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { drop.animateTo(0f) }
                throw cancelled
            }
        }
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Column(
                horizontalAlignment = fieldsAlignment,
                verticalArrangement = fieldsArrangement,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                content = fields,
            )
            FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), content = { buttons(close) })
        }
    }
}
