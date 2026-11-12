package app.itivio.data

import app.itivio.data.ign.IgnAddressSearch
import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyRequest
import app.itivio.domain.LocationKind
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IgnAddressSearchTest {
    private val query = "71 Rue de la République Montreuil 93100"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun fixture() = requireNotNull(javaClass.getResource("/ign/montreuil.json")).readText()
    private fun matches(country: String = "FR") = """[
        {"type":"ADDRESS","name":"71 Rue de la République","id":"","lat":48.852355,"lon":2.416942,"country":"$country"},
        {"type":"STOP","name":"République","id":"stop:a","lat":48.852355,"lon":2.416942,"country":"$country"}
    ]"""

    @Test fun requestMappingMergeCacheAndRouting() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine {
            seen += it
            val body = when (it.url.host) {
                "data.geopf.fr" -> {
                    assertEquals("/geocodage/search", it.url.encodedPath)
                    assertEquals(query, it.url.parameters["q"])
                    assertEquals("address", it.url.parameters["index"])
                    assertEquals("5", it.url.parameters["limit"])
                    assertEquals(setOf("q", "index", "limit"), it.url.parameters.names())
                    assertEquals("itivio/test (Android)", it.headers[HttpHeaders.UserAgent])
                    fixture()
                }
                else -> if (it.url.encodedPath.endsWith("plan")) {
                    assertEquals("48.852355,2.416942", it.url.parameters["toPlace"])
                    requireNotNull(javaClass.getResource("/transitous/plan.json")).readText()
                } else matches()
            }
            respond(body, headers = jsonHeaders)
        }
        createHttpClient(engine, "test", "https://example.org").use { transit ->
            createHttpClient(engine, "test", "", "https://data.geopf.fr/geocodage/").use { ign ->
                val repo = TransitousRoutingRepository(transit, "en", IgnAddressSearch(ign))
                val result = repo.searchLocations(" $query ", GeoPoint(48.85, 2.42))
                assertEquals(3, result.size)
                val address = result.first()
                assertEquals("ign:93048_8181_00071", address.id)
                assertEquals("71 Rue de la République", address.name)
                assertEquals("93100 Montreuil", address.area)
                assertEquals(GeoPoint(48.852355, 2.416942), address.point)
                assertEquals(LocationKind.ADDRESS, address.kind)
                assertNull(address.stopId)
                assertEquals(LocationKind.STOP, result.last().kind)
                assertEquals(result, repo.searchLocations(query.lowercase(), GeoPoint(48.85, 2.42)))
                assertEquals(2, seen.size)
                repo.planJourneys(JourneyRequest(result.last(), address))
            }
        }
        engine.close()
    }

    @Test fun foreignAndNonAddressQueriesDoNotReachIgn() = runTest {
        var country = "DE"
        val engine = MockEngine {
            assertEquals("api.transitous.org", it.url.host)
            respond(matches(country), headers = jsonHeaders)
        }
        createHttpClient(engine, "test", "").use { client ->
            val repo = TransitousRoutingRepository(client, "fr-FR", IgnAddressSearch(client))
            repo.searchLocations("71 Hauptstraße Berlin")
            country = "FR"
            repo.searchLocations("Gare de Lyon")
            repo.searchLocations("Montreuil")
            assertTrue(repo.searchLocations(" ").isEmpty())
        }
        engine.close()
    }

    @Test fun ignFailureKeepsStopsAndCanRetry() = runTest {
        for (failure in listOf("server", "json", "network")) {
            var ignCalls = 0
            val engine = MockEngine {
                if (it.url.host == "data.geopf.fr") {
                    ignCalls++
                    if (ignCalls > 1) respond(fixture(), headers = jsonHeaders)
                    else when (failure) {
                        "server" -> respond("", HttpStatusCode.TooManyRequests)
                        "json" -> respond("{broken", headers = jsonHeaders)
                        else -> throw java.io.IOException("offline")
                    }
                } else respond(matches(), headers = jsonHeaders)
            }
            createHttpClient(engine, "test", "").use { transit ->
                createHttpClient(engine, "test", "", "https://data.geopf.fr/geocodage/").use { ign ->
                    val repo = TransitousRoutingRepository(transit, "en", IgnAddressSearch(ign))
                    assertEquals("stop:a", repo.searchLocations(query).last().stopId)
                    assertTrue(repo.searchLocations(query).first().id.startsWith("ign:"))
                    assertEquals(2, ignCalls)
                }
            }
            engine.close()
        }
    }

    @Test fun malformedFeaturesAreNotDestinations() = runTest {
        val invalid = listOf(
            fixture().replace("2.416942", "200.0").replace("2.424022", "200.0"),
            fixture().replace("\"Point\"", "\"LineString\""),
            fixture().replace("0.8421210489510488", "0.2").replace("0.6676294926004228", "0.1"),
            """{"features":[{}, {"geometry":null,"properties":null}]}""",
        )
        for (body in invalid) {
            val engine = MockEngine { respond(body, headers = jsonHeaders) }
            createHttpClient(engine, "test", "", "https://data.geopf.fr/geocodage/").use {
                assertTrue(IgnAddressSearch(it).search(query).isEmpty())
            }
            engine.close()
        }
    }

    @Test fun cancellationPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val engine = MockEngine {
            if (it.url.host == "data.geopf.fr") {
                started.complete(Unit)
                awaitCancellation()
            } else respond(matches(), headers = jsonHeaders)
        }
        createHttpClient(engine, "test", "").use { transit ->
            createHttpClient(engine, "test", "", "https://data.geopf.fr/geocodage/").use { ign ->
                val repo = TransitousRoutingRepository(transit, "en", IgnAddressSearch(ign))
                var returned = false
                val job = launch { repo.searchLocations(query); returned = true }
                started.await()
                job.cancel(CancellationException("query changed"))
                job.join()
                assertFalse(returned)
            }
        }
        engine.close()
    }
}
