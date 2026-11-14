package app.itivio.data

import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.Locale

class TransitousRoutingRepositoryTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/transitous/$name.json")).readText()
    private val point = GeoPoint(52.5221252, 13.4133369)
    private val from = Location("a", "Origin", LocationKind.STOP, point, stopId = "stop:origin")
    private val to = Location("b", "Destination", LocationKind.PLACE, GeoPoint(52.5076, 13.3904))

    @Test fun userAgentBuilder() {
        assertEquals("itivio/1 (Android)", userAgent("1", ""))
        assertEquals("itivio/1 (Android)", userAgent("1", "  "))
        assertEquals("itivio/1 (+https://example.org)", userAgent("1", "https://example.org"))
    }

    @Test fun allEndpointsAndParameters() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            assertEquals("itivio/test (Android)", request.headers[HttpHeaders.UserAgent])
            assertEquals("api.transitous.org", request.url.host)
            val name = when (request.url.encodedPath) {
                "/api/v1/geocode" -> "geocode"
                "/api/v1/reverse-geocode" -> "reverse-geocode"
                "/api/v6/plan" -> "plan"
                "/api/v6/stoptimes" -> "stoptimes"
                "/api/v6/map/stops" -> "stops"
                "/api/v6/trip" -> "trip"
                else -> error("Unexpected path")
            }
            respond(fixture(name), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, { "de-DE" })
            assertEquals(5, repo.searchLocations("  Alexanderplatz  ", point).size)
            var params = seen.last().url.parameters
            assertEquals("Alexanderplatz", params["text"])
            assertEquals("10", params["numResults"])
            assertEquals("52.52,13.41", params["place"])
            assertEquals("de-DE", params["language"])
            repo.reverseGeocode(point)
            params = seen.last().url.parameters
            assertEquals("52.5221252,13.4133369", params["place"])
            assertEquals("1", params["numResults"])
            val page = repo.planJourneys(JourneyRequest(from, to, Instant.parse("2026-11-09T01:20:00Z"), true), "cursor+/=")
            assertEquals(3, page.journeys.size)
            params = seen.last().url.parameters
            assertEquals("stop:origin", params["fromPlace"])
            assertEquals("52.5076,13.3904", params["toPlace"])
            assertEquals("2026-11-09T01:20:00Z", params["time"])
            assertEquals("true", params["arriveBy"])
            assertEquals("cursor+/=", params["pageCursor"])
            assertEquals("true", params["detailedLegs"])
            assertEquals("false", params["detailedTransfers"])
            repo.planJourneys(JourneyRequest(to, from))
            assertNull(seen.last().url.parameters["time"])
            assertNull(seen.last().url.parameters["pageCursor"])
            for (name in listOf("maxTransfers", "pedestrianProfile", "useRoutedTransfers", "pedestrianSpeed")) {
                assertNull(name, seen.last().url.parameters[name])
            }
            // The default options allow each mode but flights.
            assertEquals(
                "BUS,COACH,TRAM,SUBWAY,SUBURBAN,REGIONAL_RAIL,LONG_DISTANCE,NIGHT_RAIL,HIGHSPEED_RAIL,FERRY,AERIAL_LIFT,FUNICULAR,ODM,RIDE_SHARING,OTHER",
                seen.last().url.parameters["transitModes"],
            )
            val departures = repo.nearbyDepartures(point, 700, 12)
            assertEquals(departures.sortedBy { it.time.best }, departures)
            params = seen.last().url.parameters
            assertEquals("52.5221,13.4133", params["center"])
            assertEquals("700", params["radius"])
            assertEquals("12", params["n"])
            assertEquals("true", params["exactRadius"])
            assertEquals("false", params["withAlerts"])
            repo.nearbyDepartures(point, withAlerts = true)
            assertEquals("true", seen.last().url.parameters["withAlerts"])
            val board = repo.stopDepartures("stop:board", 25)
            assertEquals(board.sortedBy { it.time.best }, board)
            params = seen.last().url.parameters
            assertEquals("stop:board", params["stopId"])
            assertEquals("25", params["n"])
            assertNull(params["center"])
            assertNull(params["time"])
            assertEquals("false", params["withAlerts"])
            repo.stopDepartures("stop:board", withAlerts = true)
            assertEquals("true", seen.last().url.parameters["withAlerts"])
            val trip = repo.trip("20261113_00:40_de-VBB_311111895")
            assertEquals(13, trip.stops.size)
            params = seen.last().url.parameters
            assertEquals("20261113_00:40_de-VBB_311111895", params["tripId"])
            assertEquals("false", params["detailedLegs"])
            assertEquals("de-DE", params["language"])
            assertTrue(repo.stopsIn(BoundingBox(52.518, 13.408, 52.525, 13.418)).all { it.kind == LocationKind.STOP })
            params = seen.last().url.parameters
            assertEquals("52.518,13.418", params["min"])
            assertEquals("52.525,13.408", params["max"])
        }
        engine.close()
    }

    @Test fun journeyOptionsBecomePlanParameters() = runTest {
        var params = Parameters.Empty
        val engine = MockEngine { request ->
            params = request.url.parameters
            respond(fixture("plan"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, { "de-DE" })
            suspend fun plan(options: JourneyOptions) = repo.planJourneys(JourneyRequest(from, to, options = options))

            // MOTIS has no parameter that excludes a mode, so the request lists the modes that stay.
            plan(JourneyOptions(excludedModes = setOf(TransitMode.BUS, TransitMode.RAIL), maxTransfers = 0, walkingSpeed = WalkingSpeed.SLOW))
            assertEquals(
                "COACH,TRAM,SUBWAY,SUBURBAN,HIGHSPEED_RAIL,FERRY,AERIAL_LIFT,FUNICULAR,ODM,RIDE_SHARING,OTHER",
                params["transitModes"],
            )
            assertEquals("0", params["maxTransfers"])
            assertEquals("0.9", params["pedestrianSpeed"])
            assertNull(params["pedestrianProfile"])

            plan(JourneyOptions(excludedModes = setOf(TransitMode.SUBWAY)))
            assertTrue(params["transitModes"].orEmpty().contains("REGIONAL_RAIL,LONG_DISTANCE,NIGHT_RAIL"))

            // The wheelchair profile needs routed transfers, and it has its own speed.
            plan(JourneyOptions(wheelchair = true, walkingSpeed = WalkingSpeed.FAST))
            assertEquals("WHEELCHAIR", params["pedestrianProfile"])
            assertEquals("true", params["useRoutedTransfers"])
            assertNull(params["pedestrianSpeed"])
            assertFalse(params["transitModes"].orEmpty().contains("AIRPLANE"))

            plan(JourneyOptions(walkingSpeed = WalkingSpeed.FAST))
            assertEquals("1.5", params["pedestrianSpeed"])
        }
        engine.close()
    }

    @Test fun lineDeparturesKeepOneLineAndDirection() = runTest {
        var params = Parameters.Empty
        val engine = MockEngine { request ->
            params = request.url.parameters
            respond(fixture("stoptimes"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, { "de-DE" })
            val time = Instant.parse("2026-11-09T01:05:00Z")
            val m5 = Line("M5", id = "de-VBB_17459_900")
            val n65 = Line("N65", id = "de-VBB_17497_700")

            // A clock time with microseconds made MOTIS start at the beginning of the service day.
            val departures = repo.lineDepartures("stop:a", m5, "0", time.plusNanos(62_073_000))
            assertEquals("stop:a", params["stopId"])
            assertEquals("2026-11-09T01:05:00Z", params["time"])
            assertNull(params["radius"])
            assertEquals(2, departures.size)
            assertTrue(departures.all { it.line.id == m5.id && it.directionId == "0" })
            assertEquals(departures.sortedBy { it.time.best }, departures)

            assertEquals(1, repo.lineDepartures("stop:a", m5, "0", time, limit = 1).size)
            // N65 runs in both directions at this stop group.
            assertEquals(listOf("0"), repo.lineDepartures("stop:a", n65, "0", time).map { it.directionId })
            assertEquals(2, repo.lineDepartures("stop:a", n65, null, time).size)
            // Without a route ID the short name identifies the line.
            assertEquals(2, repo.lineDepartures("stop:a", Line("M5"), "0", time).size)
            assertTrue(repo.lineDepartures("stop:a", Line("M5", id = "other"), "0", time).isEmpty())
        }
        engine.close()
    }

    @Test fun lineDeparturesKeepTripsThatStopAtTheAlightingStop() = runTest {
        var params = Parameters.Empty
        var name = "stoptimes-idfm"
        val engine = MockEngine { request ->
            params = request.url.parameters
            respond(fixture(name), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, { "fr" })
            val time = Instant.parse("2026-11-12T17:35:00Z")
            val idfm = "fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM"
            val rerA = Line("A", id = "$idfm:C01742")
            val nation = Stop("Nation", GeoPoint(48.848, 2.396), stopId = "$idfm:473920")

            // IDFM gives direction 0 to westbound UZEL and eastbound QOKA, and 1 to eastbound NOTE.
            val departures = repo.lineDepartures("$idfm:473935", rerA, "0", time, alightAt = nation)
            assertEquals("true", params["fetchStops"])
            assertEquals(listOf("QOKA52", "NOTE54", "ORKA54"), departures.map { it.tripCode })
            // Another platform of the same station also matches.
            val station = nation.copy(stopId = "$idfm:other", stationId = "$idfm:71673")
            assertEquals(3, repo.lineDepartures("$idfm:473935", rerA, "0", time, alightAt = station).size)
            assertTrue(repo.lineDepartures("$idfm:473935", rerA, "0", time, alightAt = nation.copy(stopId = "$idfm:other")).isEmpty())

            // QOKA52 skips Nation and leaves the list. NOTE54 is cancelled and stays as cancelled.
            name = "stoptimes-idfm-cancelled"
            val changed = repo.lineDepartures("$idfm:473935", rerA, "0", time, alightAt = nation)
            assertEquals(listOf("NOTE54" to true, "ORKA54" to false), changed.map { it.tripCode to it.cancelled })
            name = "stoptimes-idfm"

            repo.lineDepartures("$idfm:473935", rerA, "0", time)
            assertNull(params["fetchStops"])
            // Without next stops in the response, the direction ID decides.
            name = "stoptimes"
            val m5 = Line("M5", id = "de-VBB_17459_900")
            assertEquals(2, repo.lineDepartures("stop:a", m5, "0", Instant.parse("2026-11-09T01:00:00Z"), alightAt = nation).size)
            assertTrue(repo.lineDepartures("stop:a", m5, "1", Instant.parse("2026-11-09T01:00:00Z"), alightAt = nation).isEmpty())
        }
        engine.close()
    }

    @Test fun lineDeparturesPagePastOtherLinesAndDeduplicateEvents() = runTest {
        val events = Json.parseToJsonElement(fixture("stoptimes")).jsonObject.getValue("stopTimes").jsonArray
        val matching = events.filter { it.jsonObject["routeShortName"]?.jsonPrimitive?.content == "M5" }
        val other = events.filterNot { it in matching }
        val cursors = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val cursor = request.url.parameters["pageCursor"]
            cursors += cursor
            assertEquals("2026-11-09T01:00:00Z", request.url.parameters["time"])
            val rows = when (cursor) { null -> other; "next" -> listOf(matching.first(), matching.first()); else -> matching }
            val next = when (cursor) { null -> "next"; "next" -> "last"; else -> "unused" }
            respond(JsonObject(mapOf("stopTimes" to JsonArray(rows), "nextPageCursor" to JsonPrimitive(next))).toString(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val result = TransitousRoutingRepository(client).lineDepartures("stop:a", Line("M5"), "0", Instant.parse("2026-11-09T01:00:00Z"), limit = 2)
            assertEquals(2, result.size)
            assertEquals(listOf(null, "next", "last"), cursors)
        }
        engine.close()
    }

    @Test fun lineDeparturePagingStopsOnRepeatedCursorsAndKeepsPartialResultsOnFailure() = runTest {
        val events = Json.parseToJsonElement(fixture("stoptimes")).jsonObject.getValue("stopTimes").jsonArray
        val event = events.first { it.jsonObject["routeShortName"]?.jsonPrimitive?.content == "M5" }
        var calls = 0
        var fail = false
        val engine = MockEngine {
            calls++
            if (fail && calls == 2) throw IOException("connection lost")
            else respond(JsonObject(mapOf("stopTimes" to JsonArray(listOf(event)), "nextPageCursor" to JsonPrimitive("same"))).toString(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client)
            assertEquals(1, repo.lineDepartures("stop:a", Line("M5"), "0", Instant.parse("2026-11-09T01:00:00Z")).size)
            assertEquals(2, calls)
            fail = true
            calls = 0
            assertEquals(1, repo.lineDepartures("stop:a", Line("M5"), "0", Instant.parse("2026-11-09T01:00:00Z")).size)
            assertEquals(2, calls)
        }
        engine.close()
    }

    @Test fun blankAndCacheAndBiasKeys() = runTest {
        var count = 0
        val engine = MockEngine {
            count++
            respond(fixture("geocode"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, { "en" })
            assertTrue(repo.searchLocations(" \t ", point).isEmpty())
            assertEquals(0, count)
            val first = repo.searchLocations("Alexanderplatz", point)
            assertEquals(first, repo.searchLocations(" alexanderplatz ", point.copy(latitude = 52.5222)))
            assertEquals(1, count)
            repo.searchLocations("Alexanderplatz", null)
            assertEquals(2, count)
            repo.searchLocations("Alexanderplatz", point.copy(latitude = 51.0))
            assertEquals(3, count)
            repeat(65) { repo.searchLocations("query$it") }
            repo.searchLocations("Alexanderplatz", point)
            assertEquals(69, count)
        }
        engine.close()
    }

    @Test fun roundingDoesNotUseDeviceLocale() = runTest {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val engine = MockEngine {
                assertEquals("52.52,13.41", it.url.parameters["place"])
                respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            createHttpClient(engine, "test", "").use {
                TransitousRoutingRepository(it).searchLocations("test", point)
            }
            engine.close()
        } finally { Locale.setDefault(original) }
    }

    @Test fun serverFailure() = runTest {
        val engine = MockEngine { respond("not json", HttpStatusCode.InternalServerError) }
        createHttpClient(engine, "test", "").use {
            try {
                TransitousRoutingRepository(it).searchLocations("test")
                fail("Expected Server")
            } catch (error: RoutingException.Server) { assertEquals(500, error.status) }
        }
        engine.close()
    }

    @Test fun ioFailure() = runTest {
        val engine = MockEngine { throw IOException("offline") }
        createHttpClient(engine, "test", "").use {
            try {
                TransitousRoutingRepository(it).searchLocations("test")
                fail("Expected Offline")
            } catch (error: RoutingException.Offline) { assertTrue(error.cause is IOException) }
        }
        engine.close()
    }

    @Test fun timeoutFailures() = runTest {
        val url = "https://example.org"
        val errors = listOf(
            io.ktor.client.plugins.HttpRequestTimeoutException(url, 30_000),
            io.ktor.client.network.sockets.ConnectTimeoutException("connect timeout"),
            io.ktor.client.network.sockets.SocketTimeoutException("socket timeout"),
        )
        for (failure in errors) {
            val engine = MockEngine { throw failure }
            createHttpClient(engine, "test", "").use {
                try {
                    TransitousRoutingRepository(it).searchLocations("test")
                    fail("Expected Offline")
                } catch (error: RoutingException.Offline) { assertEquals(failure.javaClass, error.cause?.javaClass) }
            }
            engine.close()
        }
    }

    @Test fun malformedJson() = runTest {
        val engine = MockEngine { respond("{broken", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        createHttpClient(engine, "test", "").use {
            try {
                TransitousRoutingRepository(it).searchLocations("test")
                fail("Expected Unexpected")
            } catch (error: RoutingException.Unexpected) { assertNotNull(error.cause) }
        }
        engine.close()
    }

    @Test fun cancellationPropagates() = runTest {
        val engine = MockEngine { throw CancellationException("cancelled") }
        createHttpClient(engine, "test", "").use {
            try {
                TransitousRoutingRepository(it).searchLocations("test")
                fail("Expected cancellation")
            } catch (error: CancellationException) { assertEquals("cancelled", error.message) }
        }
        engine.close()
    }
}
