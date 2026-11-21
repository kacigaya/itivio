package app.itivio.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCameraFitTest {
    @Test
    fun `first result flies to the route and later results fit immediately`() {
        val fit = RouteCameraFit()
        assertFalse(fit.moveImmediately("first", results = true, walking = false))
        assertTrue(fit.moveImmediately("second", results = true, walking = false))
        fit.moveImmediately(null, results = false, walking = false)
        assertFalse(fit.moveImmediately("third", results = true, walking = false))
    }

    @Test
    fun `leaving walking fits the itinerary immediately`() {
        val fit = RouteCameraFit()
        assertFalse(fit.moveImmediately("first", results = false, walking = false))
        assertFalse(fit.moveImmediately("first-walk", results = false, walking = true))
        assertTrue(fit.moveImmediately("first", results = false, walking = false))
        assertFalse(fit.moveImmediately("second", results = false, walking = false))
    }
}
