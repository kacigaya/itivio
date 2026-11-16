package app.itivio.ui.common

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMotionStateTest {
    private val card = ScreenMotionState()
    private val below = ScreenMotionState()

    private fun gesture(progress: Float) = NavigationEventTransitionState.InProgress(
        NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = progress),
        NavigationEventTransitionState.TRANSITIONING_BACK,
    )

    /** One frame of a back change at [progress], for the card on top and the screen below it. */
    private fun back(gesture: NavigationEventTransitionState?, progress: Float) {
        card.update(gesture, running = true, entering = false, back = true, away = progress)
        below.update(gesture, running = true, entering = true, back = true, away = 1f - progress)
    }

    @Test
    fun `new screen comes up from the bottom edge and the old screen fades out`() {
        card.update(null, running = true, entering = true, back = false, away = 1f)
        below.update(null, running = true, entering = false, back = false, away = 0.25f)
        assertEquals(1f, card.drop, 1e-6f)
        assertEquals(0f, card.floatAlpha)
        assertEquals(0f, below.drop)
        assertEquals(0.5f, below.alpha, 1e-6f)

        card.update(null, running = true, entering = true, back = false, away = 0.5f)
        assertTrue(card.drop < 0.1f)

        card.update(null, running = false, entering = false, back = false, away = 0f)
        assertEquals(0f, card.drop)
        assertEquals(1f, card.floatAlpha)
    }

    @Test
    fun `back without a gesture sends the card down and shows the screen below`() {
        back(null, 0.25f)
        assertTrue(card.drop > 0f && card.drop < 0.25f)
        assertEquals(1f, card.alpha)
        assertEquals(0f, below.drop)
        assertEquals(0.5f, below.alpha, 1e-6f)

        back(null, 1f)
        assertEquals(1f, card.drop, 1e-6f)
        assertEquals(1f, below.alpha)
    }

    @Test
    fun `gesture in progress pulls the card down by its eased progress`() {
        back(gesture(0.3f), 0.3f)

        val eased = BackGestureEasing.transform(0.3f)
        assertEquals(PreviewDrop * eased, card.drop, 1e-6f)
        assertEquals(eased, below.alpha, 1e-6f)
    }

    @Test
    fun `release sends the card down from the preview`() {
        back(gesture(0.4f), 0.4f)
        val preview = card.drop

        back(null, 0.7f)
        assertTrue(card.drop > preview && card.drop < 1f)
        val eased = BackGestureEasing.transform(0.4f)
        assertEquals(eased + (1f - eased) / 2f, below.alpha, 1e-6f)

        back(null, 1f)
        assertEquals(1f, card.drop, 1e-6f)
        assertEquals(1f, below.alpha, 1e-6f)
    }

    @Test
    fun `cancel puts the card back`() {
        back(gesture(0.4f), 0.4f)

        back(null, 0.2f)
        val eased = BackGestureEasing.transform(0.4f)
        assertEquals(PreviewDrop * eased / 2f, card.drop, 1e-6f)
        assertEquals(eased / 2f, below.alpha, 1e-6f)

        back(null, 0f)
        assertEquals(0f, card.drop)
    }

    @Test
    fun `settled change forgets the gesture`() {
        back(gesture(0.4f), 0.4f)
        below.update(null, running = false, entering = false, back = true, away = 0f)

        // The next change, such as a tap on the back button, does not follow the old gesture.
        back(null, 0.25f)
        assertEquals(0.5f, below.alpha, 1e-6f)
    }

    @Test
    fun `floating parts of the card show only near the rest position`() {
        back(gesture(1f), 1f)
        assertEquals(PreviewDrop, card.drop, 1e-6f)
        assertEquals(0f, card.floatAlpha)

        back(gesture(0.05f), 0.05f)
        assertTrue(card.floatAlpha > 0f && card.floatAlpha < 1f)
    }
}
