package app.itivio.domain

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkRouteTest {
    /** A point [east] and [north] metres from a fixed origin. */
    private fun at(east: Double, north: Double) =
        GeoPoint(48.0 + north / 111_320.0, 2.0 + east / (111_320.0 * cos(Math.toRadians(48.0))))

    /** Cuts a path into steps of [size] metres, all "continue", as providers often give them. */
    private fun steps(vararg corners: Pair<Double, Double>, size: Double = 5.0, name: String? = null): List<WalkStep> =
        corners.toList().zipWithNext().flatMap { (a, b) ->
            val length = WalkRoute.distance(at(a.first, a.second), at(b.first, b.second))
            val count = (length / size).toInt().coerceAtLeast(1)
            (0 until count).map { i ->
                fun lerp(t: Double) = at(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t)
                WalkStep(length / count, name, listOf(lerp(i.toDouble() / count), lerp((i + 1.0) / count)))
            }
        }

    private fun WalkRoute.maneuvers() = instructions.map { it.maneuver }

    @Test
    fun `turns come from the geometry when every step says continue`() {
        val right = WalkRoute.of(steps(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0), emptyList())
        assertEquals(listOf(Maneuver.DEPART, Maneuver.RIGHT, Maneuver.ARRIVE), right.maneuvers())
        assertEquals(CompassDirection.NORTH, right.instructions.first().heading)
        assertEquals(100.0, right.instructions[1].startMeters, 1.0)
        assertEquals(200.0, right.lengthMeters, 1.0)

        val left = WalkRoute.of(steps(0.0 to 0.0, 0.0 to 100.0, -100.0 to 100.0), emptyList())
        assertEquals(listOf(Maneuver.DEPART, Maneuver.LEFT, Maneuver.ARRIVE), left.maneuvers())

        val slight = WalkRoute.of(steps(0.0 to 0.0, 0.0 to 100.0, 100.0 to 200.0), emptyList())
        assertEquals(Maneuver.SLIGHT_RIGHT, slight.instructions[1].maneuver)
    }

    @Test
    fun `a short jog is one bend, not two turns`() {
        // A path that crosses a road: 4 m east, then north again.
        val route = WalkRoute.of(steps(0.0 to 0.0, 0.0 to 50.0, 4.0 to 50.0, 4.0 to 100.0, size = 2.0), emptyList())
        assertEquals(listOf(Maneuver.DEPART, Maneuver.ARRIVE), route.maneuvers())
    }

    @Test
    fun `stairs get their own instruction and the walk continues after them`() {
        val before = steps(0.0 to 0.0, 0.0 to 40.0)
        val stairs = WalkStep(15.0, null, listOf(at(0.0, 40.0), at(0.0, 55.0)), kind = StepKind.STAIRS)
        val after = steps(0.0 to 55.0, 0.0 to 100.0)
        val route = WalkRoute.of(before + stairs + after, emptyList())
        assertEquals(listOf(Maneuver.DEPART, Maneuver.STAIRS, Maneuver.STRAIGHT, Maneuver.ARRIVE), route.maneuvers())
        assertEquals(40.0, route.instructions[1].startMeters, 1.0)
    }

    @Test
    fun `a turn from the provider is kept`() {
        val first = steps(0.0 to 0.0, 0.0 to 50.0)
        val turn = WalkStep(50.0, null, listOf(at(0.0, 50.0), at(0.0, 100.0)), turn = Maneuver.LEFT)
        assertEquals(listOf(Maneuver.DEPART, Maneuver.LEFT, Maneuver.ARRIVE), WalkRoute.of(first + turn, emptyList()).maneuvers())
    }

    @Test
    fun `the street name is the one that covers most of the instruction`() {
        val named = steps(0.0 to 0.0, 0.0 to 90.0, name = "Rue de Rivoli")
        val bit = WalkStep(2.0, "Allée", listOf(at(0.0, 90.0), at(0.0, 92.0)))
        val route = WalkRoute.of(named + bit + steps(0.0 to 92.0, 100.0 to 92.0), emptyList())
        assertEquals("Rue de Rivoli", route.instructions[0].streetName)
        // The way after the turn has no name: the fallback text names only the turn.
        assertNull(route.instructions[1].streetName)
    }

    @Test
    fun `without steps the geometry gives one instruction`() {
        val route = WalkRoute.of(emptyList(), listOf(at(0.0, 0.0), at(100.0, 0.0)))
        assertEquals(listOf(Maneuver.DEPART, Maneuver.ARRIVE), route.maneuvers())
        assertEquals(CompassDirection.EAST, route.instructions.first().heading)
    }

    @Test
    fun `progress projects the position on the route`() {
        val route = WalkRoute.of(steps(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0), emptyList())
        val near = route.progress(at(10.0, 50.0))
        assertEquals(50.0, near.alongMeters, 1.0)
        assertEquals(10.0, near.offRouteMeters, 1.0)
        assertFalse(near.isOffRoute)
        assertTrue(route.progress(at(-80.0, 50.0)).isOffRoute)

        // The start instruction shows until the walker is on the way, then the next turn.
        assertEquals(0, route.nextInstructionIndex(5.0))
        assertEquals(1, route.nextInstructionIndex(50.0))
        assertEquals(2, route.nextInstructionIndex(150.0))
        assertEquals(2, route.nextInstructionIndex(500.0))
    }

    @Test
    fun `split cuts the route at the walker and keeps both parts joined`() {
        val route = WalkRoute.of(emptyList(), listOf(at(0.0, 0.0), at(0.0, 100.0), at(100.0, 100.0)))
        val (walked, ahead) = route.split(150.0)
        assertEquals(3, walked.size)
        assertEquals(walked.last(), ahead.first())
        assertEquals(50.0, WalkRoute.distance(at(0.0, 100.0), walked.last()), 0.5)
        assertEquals(at(100.0, 100.0), ahead.last())

        // Before the start, nothing is walked. Past the end, nothing is ahead.
        val (none, all) = route.split(-5.0)
        assertEquals(listOf(route.points.first(), route.points.first()), none)
        assertEquals(route.points, all)
        val (whole, rest) = route.split(500.0)
        assertEquals(3, whole.size)
        assertEquals(0.0, WalkRoute.distance(rest.first(), rest.last()), 0.01)
    }

    @Test
    fun `a position near the route snaps to it, a far one stays`() {
        val route = WalkRoute.of(emptyList(), listOf(at(0.0, 0.0), at(0.0, 100.0)))
        val near = route.snap(at(15.0, 40.0))
        assertEquals(0.0, WalkRoute.distance(at(0.0, 40.0), near), 0.5)
        val far = at(30.0, 40.0)
        assertEquals(far, route.snap(far))
    }

    @Test
    fun `turn angles map to maneuvers on both sides`() {
        assertEquals(Maneuver.STRAIGHT, WalkRoute.turn(20.0))
        assertEquals(Maneuver.SLIGHT_LEFT, WalkRoute.turn(-45.0))
        assertEquals(Maneuver.RIGHT, WalkRoute.turn(90.0))
        assertEquals(Maneuver.SHARP_LEFT, WalkRoute.turn(-140.0))
        assertEquals(Maneuver.U_TURN, WalkRoute.turn(175.0))
        // 350° is 10° to the left.
        assertEquals(Maneuver.STRAIGHT, WalkRoute.turn(350.0))
        assertEquals(Maneuver.LEFT, WalkRoute.turn(270.0))
    }
}
