package app.itivio.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** The time of a forward screen change. */
private const val ForwardMillis = 450

/**
 * The time of a back screen change from start to end. A release continues the change from the
 * point of the gesture, so a short gesture gets a longer commit animation.
 */
internal const val BackMillis = 300

/** How far down the card is at the end of the gesture preview, as a part of the screen height. */
internal const val PreviewDrop = 0.15f

/** The floating parts of a card show only when the card is this near to its rest position. */
private const val FloatRange = 0.125f

/** The Material emphasized easings: a card slows down when it comes in and speeds up when it goes. */
private val CardEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val CardExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** The motion of the screen around the content. Null outside the app root, such as in tests. */
private val LocalScreenMotion = compositionLocalOf<(() -> ScreenMotionState)?> { null }

private const val FullScreenPageKey = "itivio.fullScreenPage"
private const val OpaqueUnderlayKey = "itivio.opaqueUnderlay"
internal val FullScreenPage = mapOf(FullScreenPageKey to true)
internal val DisruptionsPage = FullScreenPage + (OpaqueUnderlayKey to true)

/**
 * The direction of the screen change in progress. NavDisplay gives it: each of its three
 * transition specs must return [transform]. The transform keeps the two screens until the change
 * ends, and [screenMotionDecorator] gives all the motion.
 */
class ScreenChange {
    internal var back = false
        private set
    internal var keepBackgroundOpaque = false
        private set

    fun <T : Any> transform(scope: AnimatedContentTransitionScope<Scene<T>>, back: Boolean): ContentTransform {
        this.back = back
        keepBackgroundOpaque = keepsUnderlayOpaque(
            scope.initialState.entries.singleOrNull()?.metadata.orEmpty(),
            scope.targetState.entries.singleOrNull()?.metadata.orEmpty(),
        )
        return with(scope) { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished }
    }
}

internal fun keepsUnderlayOpaque(from: Map<String, Any>, to: Map<String, Any>): Boolean =
    from[OpaqueUnderlayKey] == true || to[OpaqueUnderlayKey] == true ||
        (from[FullScreenPageKey] == true && to[FullScreenPageKey] == true)

/**
 * Moves the screen on top as a card. A new screen comes up from the bottom edge, and the screen
 * below it can fade. "Back" sends the card down again. A back gesture pulls the card down by
 * [PreviewDrop] and shows the screen below. A release sends the card down. A cancelled gesture
 * puts the card back.
 *
 * NavDisplay moves its transition with the gesture, so the gesture and the button share one motion.
 */
fun <T : Any> screenMotionDecorator(change: ScreenChange): NavEntryDecorator<T> = NavEntryDecorator { entry ->
    val transition = LocalNavAnimatedContentScope.current.transition
    val gesture = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher?.transitionState?.collectAsState()
    // Zero while the screen is visible, one before it comes in or after it goes. NavDisplay seeks
    // the transition with the gesture, so during the gesture this is its progress.
    val away = transition.animateFloat(
        transitionSpec = { tween(if (change.back) BackMillis else ForwardMillis, easing = LinearEasing) },
        label = "screen motion",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val motion = remember(transition, gesture, away) {
        val state = ScreenMotionState()
        val read = {
            state.update(
                gesture = gesture?.value,
                running = transition.currentState != transition.targetState,
                entering = transition.currentState == EnterExitState.PreEnter,
                back = change.back,
                away = away.value,
                keepBackgroundOpaque = change.keepBackgroundOpaque,
            )
            state
        }
        read
    }
    CompositionLocalProvider(LocalScreenMotion provides motion) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val now = motion()
                translationY = now.drop * size.height
                alpha = now.alpha
            },
        ) { entry.Content() }
    }
}

/**
 * For a full-screen page. The page gets the top corners of the screen while it moves, so it has
 * the shape of a card. The corners go in the last part of the way, so they do not go at once.
 */
@Composable
fun Modifier.screenCard(): Modifier {
    val motion = LocalScreenMotion.current ?: return this
    val radius = screenCornerRadius()
    return graphicsLayer {
        val corner = radius.toPx() * (motion().drop * 10f).coerceAtMost(1f)
        shape = RoundedCornerShape(topStart = corner, topEnd = corner)
        clip = corner > 0f
    }
}

/**
 * For the parts of a screen that float on the map, away from its sheet. They move with the card
 * but show only near the rest position, so they do not cross the map.
 */
@Composable
fun Modifier.screenFloat(): Modifier {
    val motion = LocalScreenMotion.current ?: return this
    return graphicsLayer { alpha = motion().floatAlpha }
}

/**
 * Gives [value] while the screen is in view, then its last value while the screen goes out. "Back"
 * resets the data of some screens at once, and the card must not change while it goes down.
 */
@Composable
fun <T> keptWhileLeaving(value: T): T {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState == EnterExitState.PostExit
    return remember { mutableStateOf(value) }.apply { if (!leaving) this.value = value }.value
}

/** The motion of one screen during a screen change. */
internal class ScreenMotionState {
    /** True while the change of the screen started with a back gesture. */
    private var fromGesture = false

    /** The progress of the change when the user released the gesture. */
    private var release = 0f

    /** How far down the screen is as a card, from 0 (at rest) to 1 (below the edge of the screen). */
    var drop = 0f
        private set

    /** The opacity of the screen when it is below the card. */
    var alpha = 1f
        private set

    /** The opacity of the floating parts of the card. */
    val floatAlpha get() = (1f - drop / FloatRange).coerceAtLeast(0f)

    /**
     * Reads one frame. [back] is the direction of the change. [entering] is true for the screen
     * that comes into view. [away] is 0 when the screen is in view and 1 when it is not.
     */
    fun update(
        gesture: NavigationEventTransitionState?,
        running: Boolean,
        entering: Boolean,
        back: Boolean,
        away: Float,
        keepBackgroundOpaque: Boolean = false,
    ) {
        val inGesture = gesture is NavigationEventTransitionState.InProgress
        val progress = if (entering) 1f - away else away
        if (!running) fromGesture = false
        if (running && back && inGesture) {
            fromGesture = true
            release = progress
        }
        drop = 0f
        alpha = 1f
        if (!running) return
        // The screen on top of the two: the new screen of a forward change, the old one of "Back".
        val card = entering != back
        if (!fromGesture) {
            if (card) {
                drop = if (entering) 1f - CardEnterEasing.transform(progress) else CardExitEasing.transform(progress)
            } else if (!keepBackgroundOpaque) {
                // The screen below changes in the first half, so two headers do not show together.
                alpha = (if (entering) progress * 2f else 1f - progress * 2f).coerceIn(0f, 1f)
            }
            return
        }
        val atRelease = BackGestureEasing.transform(release)
        val amount = when {
            inGesture -> BackGestureEasing.transform(progress)
            progress < release -> atRelease * progress / release
            else -> atRelease
        }
        val commit = if (!inGesture && progress > release) (progress - release) / (1f - release) else 0f
        if (card) {
            val preview = PreviewDrop * amount
            drop = preview + (1f - preview) * CardExitEasing.transform(commit)
        } else if (!keepBackgroundOpaque) {
            alpha = amount + (1f - amount) * commit
        }
    }
}
