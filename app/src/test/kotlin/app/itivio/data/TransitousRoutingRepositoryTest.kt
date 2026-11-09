package app.itivio.data

import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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
        assertEquals("Itivio/1 (Android)", userAgent("1", ""))
        assertEquals("Itivio/1 (Android)", userAgent("1", "  "))
        assertEquals("Itivio/1 (+https://example.org)", userAgent("1", "https://example.org"))
    }

    @Test fun allEndpointsAndParameters() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            assertEquals("Itivio/test (Android)", request.headers[HttpHeaders.UserAgent])
            assertEquals("api.transitous.org", request.url.host)
            val name = when (request.url.encodedPath) {
                "/api/v1/geocode" -> "geocode"
                "/api/v1/reverse-geocode" -> "reverse-geocode"
                "/api/v6/plan" -> "plan"
                "/api/v6/stoptimes" -> "stoptimes"
                "/api/v6/map/stops" -> "stops"
                else -> error("Unexpected path")
            }
            respond(fixture(name), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, "de-DE")
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
            val departures = repo.nearbyDepartures(point, 700, 12)
            assertEquals(departures.sortedBy { it.time.best }, departures)
            params = seen.last().url.parameters
            assertEquals("52.5221,13.4133", params["center"])
            assertEquals("700", params["radius"])
            assertEquals("12", params["n"])
            assertEquals("true", params["exactRadius"])
            assertEquals("false", params["withAlerts"])
            assertTrue(repo.stopsIn(BoundingBox(52.518, 13.408, 52.525, 13.418)).all { it.kind == LocationKind.STOP })
            params = seen.last().url.parameters
            assertEquals("52.518,13.418", params["min"])
            assertEquals("52.525,13.408", params["max"])
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
            val repo = TransitousRoutingRepository(client, "en")
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
