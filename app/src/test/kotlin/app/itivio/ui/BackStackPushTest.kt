package app.itivio.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackPushTest {
    @Test
    fun `a repeated tap opens the screen once`() {
        val backStack = NavBackStack<NavKey>(ExploreRoute)

        backStack.push(ResultsRoute)
        backStack.push(ResultsRoute)
        backStack.push(ItineraryRoute("a"))
        backStack.push(ItineraryRoute("a"))

        assertEquals(listOf(ExploreRoute, ResultsRoute, ItineraryRoute("a")), backStack.toList())
    }

    @Test
    fun `another screen still opens`() {
        val backStack = NavBackStack<NavKey>(ExploreRoute, StopRoute("s1", "A", 0.0, 0.0))

        backStack.push(StopRoute("s2", "B", 0.0, 0.0))

        assertEquals(3, backStack.size)
    }
}
