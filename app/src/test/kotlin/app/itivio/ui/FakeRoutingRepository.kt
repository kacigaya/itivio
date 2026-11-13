package app.itivio.ui

import app.itivio.domain.BoundingBox
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyPage
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.RoutingRepository
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.domain.Trip
import java.time.Instant

class FakeRoutingRepository : RoutingRepository {
    val nearbyCalls = mutableListOf<GeoPoint>()
    var nearbyResult: suspend (GeoPoint) -> List<Departure> = { emptyList() }
    val lineCalls = mutableListOf<Pair<String, Instant>>()
    var lineResult: suspend (String) -> List<Departure> = { emptyList() }
    val searches = mutableListOf<Pair<String, GeoPoint?>>()
    val plans = mutableListOf<Pair<JourneyRequest, String?>>()
    var searchResult: suspend (String) -> List<Location> = { listOf(location(it)) }
    var planResult: suspend (JourneyRequest, String?) -> JourneyPage = { _, _ ->
        JourneyPage(listOf(journey("a", minutes = 20, transfers = 1, walk = 300.0)), earlierCursor = null, laterCursor = "next")
    }

    override suspend fun searchLocations(query: String, near: GeoPoint?): List<Location> {
        searches += query to near
        return searchResult(query)
    }

    override suspend fun reverseGeocode(point: GeoPoint): Location? = null

    override suspend fun planJourneys(request: JourneyRequest, cursor: String?): JourneyPage {
        plans += request to cursor
        return planResult(request, cursor)
    }

    /** The points of the calls that asked for alerts. */
    val nearbyAlertCalls = mutableListOf<GeoPoint>()

    override suspend fun nearbyDepartures(point: GeoPoint, radiusMeters: Int, limit: Int, withAlerts: Boolean): List<Departure> {
        nearbyCalls += point
        if (withAlerts) nearbyAlertCalls += point
        return nearbyResult(point)
    }

    override suspend fun lineDepartures(
        stopId: String,
        line: Line,
        directionId: String?,
        from: Instant,
        limit: Int,
        alightAt: Stop?,
    ): List<Departure> {
        lineCalls += stopId to from
        return lineResult(stopId)
    }

    val stopCalls = mutableListOf<String>()
    var stopResult: suspend (String) -> List<Departure> = { emptyList() }
    val tripCalls = mutableListOf<String>()
    var tripResult: suspend (String) -> Trip = { Trip(emptyList()) }
    val boxes = mutableListOf<BoundingBox>()
    var stopsResult: suspend (BoundingBox) -> List<Location> = { emptyList() }

    /** The stops of the calls that asked for alerts. */
    val stopAlertCalls = mutableListOf<String>()

    override suspend fun stopDepartures(stopId: String, limit: Int, withAlerts: Boolean): List<Departure> {
        stopCalls += stopId
        if (withAlerts) stopAlertCalls += stopId
        return stopResult(stopId)
    }

    override suspend fun trip(tripId: String): Trip {
        tripCalls += tripId
        return tripResult(tripId)
    }

    override suspend fun stopsIn(box: BoundingBox): List<Location> {
        boxes += box
        return stopsResult(box)
    }

    companion object {
        fun location(name: String, stopId: String? = null) =
            Location(id = "id-$name", name = name, kind = LocationKind.STOP, point = GeoPoint(52.5, 13.4), stopId = stopId)

        private val base: Instant = Instant.parse("2026-11-09T10:00:00Z")

        fun journey(id: String, minutes: Long, transfers: Int, walk: Double): Journey {
            val start = StopTime(base)
            val mid = StopTime(base.plusSeconds(300))
            val end = StopTime(base.plusSeconds(minutes * 60))
            val a = Stop("A", GeoPoint(52.5, 13.4))
            val b = Stop("B", GeoPoint(52.51, 13.41))
            val c = Stop("C", GeoPoint(52.52, 13.42))
            val legs = listOf(
                Leg.Street(TransitMode.WALK, a, b, start, mid, emptyList(), walk),
                Leg.Transit(TransitMode.BUS, b, c, mid, end, emptyList(), Line("M41"), "Somewhere", "trip-$id", emptyList()),
            )
            return Journey(id = id, legs = legs, departure = start, arrival = end, transfers = transfers)
        }
    }
}
