package app.itivio.ui

import app.itivio.domain.GeoPoint
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.common.chainLegs
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainLegsTest {
    private val t0 = Instant.parse("2026-11-09T10:00:00Z")
    private val stop = Stop("Station", GeoPoint(52.5, 13.4))

    // Only the duration of a leg matters to the chain.
    private fun time(minutes: Long) = StopTime(t0.plusSeconds(minutes * 60))
    private fun walk(minutes: Long) = Leg.Street(TransitMode.WALK, stop, stop, time(0), time(minutes), emptyList(), 100.0)
    private fun ride(name: String) = Leg.Transit(TransitMode.BUS, stop, stop, time(0), time(10), emptyList(), Line(name), null, name, emptyList())

    @Test
    fun `a short transfer walk does not show, and a long one shows`() {
        val legs = listOf(walk(4), ride("A"), walk(4), ride("B"), walk(5), ride("C"), walk(2))
        assertEquals(listOf(0, 1, 3, 4, 5, 6), chainLegs(legs).map(legs::indexOf))
    }

    @Test
    fun `a first or last walk under two minutes does not show`() {
        val legs = listOf(walk(1), ride("A"), walk(1))
        assertEquals(listOf(1), chainLegs(legs).map(legs::indexOf))
    }
}
