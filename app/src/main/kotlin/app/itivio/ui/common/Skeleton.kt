package app.itivio.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.itivio.R

// The opacity of a block changes between the hover and the drag state layer of Material 3.
private const val PULSE_LOW = 0.08f
private const val PULSE_HIGH = 0.16f
private const val PULSE_MILLIS = 900

/** The opacity of the blocks. Outside a [Skeleton], the blocks do not pulse. */
private val LocalSkeletonAlpha = compositionLocalOf<State<Float>> { mutableFloatStateOf(PULSE_HIGH) }

/**
 * The loading state of a list: a column of placeholders with the layout of the content to come,
 * so the list does not jump when the content arrives. A screen reader gets one "Loading" label.
 * All the blocks in [content] pulse in step. With system animations off, they do not pulse.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val label = stringResource(R.string.loading)
    val alpha = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    CompositionLocalProvider(LocalSkeletonAlpha provides alpha) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
            content = content,
        )
    }
}

/** A placeholder for a badge, an icon, or a tag. [modifier] gives its size. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    val alpha = LocalSkeletonAlpha.current
    val color = MaterialTheme.colorScheme.onSurface
    // The draw phase reads the opacity, so a pulse does not recompose the list.
    Box(modifier.clip(shape).drawBehind { drawRect(color, alpha = alpha.value) })
}

/**
 * A placeholder for one line of text in [style]. It takes the line height of the style, so it
 * grows with the text size of the user. [modifier] gives its width.
 */
@Composable
fun SkeletonText(style: TextStyle, modifier: Modifier = Modifier) {
    val (line, bar) = with(LocalDensity.current) { style.lineHeight.toDp() to style.fontSize.toDp() * 0.7f }
    Box(modifier.height(line), contentAlignment = Alignment.CenterStart) {
        SkeletonBlock(Modifier.fillMaxWidth().height(bar))
    }
}
