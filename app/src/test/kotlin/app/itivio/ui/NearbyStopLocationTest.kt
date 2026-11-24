package app.itivio.ui

import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.LocationKind
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.nearby.toLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbyStopLocationTest {
    private fun departure(stop: Stop, mode: TransitMode) =
        Departure(stop, Line("U2"), mode, "Ruhleben", StopTime(Instant.EPOCH), false, "t")

    @Test
    fun `a nearby stop plans to its station with all its modes`() {
        val platform = Stop("Alexanderplatz", GeoPoint(52.52, 13.41), stopId = "platform-2", stationId = "station")
        val location = StopDepartures(
            "Alexanderplatz",
            listOf(departure(platform, TransitMode.SUBWAY), departure(platform.copy(stopId = "platform-12"), TransitMode.SUBURBAN)),
        ).toLocation()!!

        assertEquals("station", location.stopId)
        assertEquals(LocationKind.STOP, location.kind)
        assertEquals(setOf(TransitMode.SUBWAY, TransitMode.SUBURBAN), location.modes)
    }

    @Test
    fun `a stop without an ID is not a place`() {
        val stop = Stop("Somewhere", GeoPoint(0.0, 0.0))
        assertNull(StopDepartures("Somewhere", listOf(departure(stop, TransitMode.BUS))).toLocation())
    }
}
