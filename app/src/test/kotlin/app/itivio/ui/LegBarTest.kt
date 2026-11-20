package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.results.barSegments
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegBarTest {
    private val t0 = Instant.parse("2026-11-09T10:00:00Z")
    private val stop = Stop("A", GeoPoint(52.5, 13.4))

    private fun at(minutes: Long) = StopTime(t0.plusSeconds(minutes * 60))
    private fun walk(from: Long, to: Long) = Leg.Street(TransitMode.WALK, stop, stop, at(from), at(to), emptyList(), 100.0)
    private fun ride(from: Long, to: Long) =
        Leg.Transit(TransitMode.BUS, stop, stop, at(from), at(to), emptyList(), Line("M41"), null, null, emptyList())

    @Test
    fun `legs take their share of the journey time and a wait stays empty`() {
        val legs = listOf(walk(0, 5), ride(10, 20))
        val segments = barSegments(Journey("j", legs, at(0), at(20), transfers = 0))

        assertEquals(listOf(0f to 0.25f, 0.5f to 1f), segments.map { it.start to it.end })
    }

    @Test
    fun `a leg without time and a journey without time give no segment`() {
        val legs = listOf(walk(0, 0), ride(0, 10))
        assertEquals(1, barSegments(Journey("j", legs, at(0), at(10), transfers = 0)).size)
        assertTrue(barSegments(Journey("j", listOf(walk(0, 0)), at(0), at(0), transfers = 0)).isEmpty())
    }
}
