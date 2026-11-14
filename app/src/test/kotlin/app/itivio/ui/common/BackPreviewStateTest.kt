package app.itivio.ui.common

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import org.junit.Assert.assertEquals
import org.junit.Test

class BackPreviewStateTest {
    private val state = BackPreviewState()

    private fun gesture(progress: Float, edge: Int = NavigationEvent.EDGE_LEFT) =
        NavigationEventTransitionState.InProgress(
            NavigationEvent(swipeEdge = edge, progress = progress),
            NavigationEventTransitionState.TRANSITIONING_BACK,
        )

    @Test
    fun `transition without a gesture shows no preview`() {
        state.update(null, running = true, progress = 0.5f)

        assertEquals(0f, state.amount)
        assertEquals(0f, state.commit)
    }

    @Test
    fun `gesture in progress follows its eased progress and edge`() {
        state.update(gesture(0.3f, NavigationEvent.EDGE_RIGHT), running = true, progress = 0.3f)

        assertEquals(BackGestureEasing.transform(0.3f), state.amount, 1e-6f)
        assertEquals(0f, state.commit)
        assertEquals(NavigationEvent.EDGE_RIGHT, state.edge)
    }

    @Test
    fun `release keeps the preview and runs the commit to the end`() {
        state.update(gesture(0.4f), running = true, progress = 0.4f)

        state.update(null, running = true, progress = 0.7f)
        assertEquals(BackGestureEasing.transform(0.4f), state.amount, 1e-6f)
        assertEquals(0.5f, state.commit, 1e-6f)

        state.update(null, running = true, progress = 1f)
        assertEquals(1f, state.commit, 1e-6f)
    }

    @Test
    fun `cancel puts the card back`() {
        state.update(gesture(0.4f), running = true, progress = 0.4f)

        state.update(null, running = true, progress = 0.2f)
        assertEquals(BackGestureEasing.transform(0.4f) / 2f, state.amount, 1e-6f)
        assertEquals(0f, state.commit)

        state.update(null, running = true, progress = 0f)
        assertEquals(0f, state.amount)
    }

    @Test
    fun `settled transition forgets the gesture`() {
        state.update(gesture(0.4f), running = true, progress = 0.4f)
        state.update(null, running = false, progress = 0f)

        // The next transition, such as a tap on the back button, has no preview.
        state.update(null, running = true, progress = 0.5f)
        assertEquals(0f, state.amount)
        assertEquals(0f, state.commit)
    }
}
