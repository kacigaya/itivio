package app.itivio.ui.common

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Maps the progress of a back gesture to its preview. Most of the movement comes at the start of
 * the gesture, as in the back animations of the system.
 */
val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
