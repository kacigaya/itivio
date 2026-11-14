package app.itivio.ui.common

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * The time of a back transition from start to end. A release continues the transition from the
 * point of the gesture, so a short gesture gets a longer commit animation.
 */
private const val BackPreviewMillis = 300

/** The scale of the card at the end of the preview. */
private const val CardScale = 0.9f

/** The Material scrim opacity, on the screen below the card at the end of the preview. */
private const val ScrimAlpha = 0.32f

/** The space the card keeps from the edge of the screen when it moves. */
private val CardMargin = 8.dp

/**
 * Shows the screen on top as a card during a back gesture, as in the system settings. The card
 * shrinks to 90%, moves away from the edge of the gesture, and gets the corners of the screen.
 * The screen below it is dim. A release fades the card out and removes the dim. A cancelled
 * gesture puts the card back. Other transitions do not change.
 *
 * NavDisplay moves its transition with the gesture. NavDisplay must use
 * `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished` for the
 * predictive pop, so this decorator gives all the motion.
 */
fun <T : Any> backPreviewDecorator(): NavEntryDecorator<T> = NavEntryDecorator { entry ->
    Box(Modifier.fillMaxSize().backPreview()) { entry.Content() }
}

@Composable
private fun Modifier.backPreview(): Modifier {
    val transition = LocalNavAnimatedContentScope.current.transition
    val gesture = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher?.transitionState?.collectAsState()
    // Zero while the screen is visible, one before it comes in or after it goes. NavDisplay seeks
    // the transition with the gesture, so during the gesture this is its progress.
    val position = transition.animateFloat(
        transitionSpec = { tween(BackPreviewMillis, easing = LinearEasing) },
        label = "back preview",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val preview = remember { BackPreviewState() }
    val radius = screenCornerRadius()
    val scrim = MaterialTheme.colorScheme.scrim
    return graphicsLayer {
        val entering = transition.currentState == EnterExitState.PreEnter
        preview.update(gesture?.value, transition.currentState != transition.targetState, position.value)
        if (entering || preview.amount == 0f && preview.commit == 0f) {
            clip = false
            return@graphicsLayer
        }
        val scale = 1f - (1f - CardScale) * preview.amount
        scaleX = scale
        scaleY = scale
        val shift = (size.width * (1f - CardScale) / 2f - CardMargin.toPx()).coerceAtLeast(0f)
        translationX = shift * preview.amount * when (preview.edge) {
            NavigationEvent.EDGE_LEFT -> 1f
            NavigationEvent.EDGE_RIGHT -> -1f
            else -> 0f
        }
        alpha = 1f - preview.commit
        // The layer scales the shape too, so the card keeps the radius of the screen. The corners
        // come in during the first part of the gesture, so they do not appear at once.
        shape = RoundedCornerShape(radius.toPx() / scale * (preview.amount * 10f).coerceAtMost(1f))
        clip = true
    }.drawWithContent {
        drawContent()
        if (transition.currentState == EnterExitState.PreEnter) {
            preview.update(gesture?.value, transition.currentState != transition.targetState, 1f - position.value)
            val dim = ScrimAlpha * preview.amount * (1f - preview.commit)
            if (dim > 0f) drawRect(scrim, alpha = dim)
        }
    }
}

/** The state of one screen during a back gesture. */
internal class BackPreviewState {
    /** True while the transition of the screen started with a back gesture. */
    private var fromGesture = false

    /** The progress of the transition when the user released the gesture. */
    private var release = 0f

    /** The edge where the gesture started, from [NavigationEvent]. */
    var edge = NavigationEvent.EDGE_NONE
        private set

    /** How far the preview goes, from 0 (the screen at rest) to 1 (the full card). */
    var amount = 0f
        private set

    /** How far the commit animation goes after a release, from 0 to 1. */
    var commit = 0f
        private set

    /**
     * Reads one frame. [progress] goes from 0, with the screen at rest, to 1, at the end of the
     * back transition. It runs back to 0 when the user cancels the gesture.
     */
    fun update(gesture: NavigationEventTransitionState?, running: Boolean, progress: Float) {
        val inGesture = gesture is NavigationEventTransitionState.InProgress
        if (!running) fromGesture = false
        if (running && inGesture) {
            fromGesture = true
            release = progress
            edge = gesture.latestEvent.swipeEdge
        }
        if (!fromGesture) {
            amount = 0f
            commit = 0f
            return
        }
        val atRelease = BackGestureEasing.transform(release)
        amount = when {
            inGesture -> BackGestureEasing.transform(progress)
            progress < release -> atRelease * progress / release
            else -> atRelease
        }
        commit = if (!inGesture && progress > release) (progress - release) / (1f - release) else 0f
    }
}
