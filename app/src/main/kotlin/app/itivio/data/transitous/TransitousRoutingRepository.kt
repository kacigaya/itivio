package app.itivio.data.transitous

import app.itivio.domain.BoundingBox
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyPage
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Line
import app.itivio.domain.Location
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.util.Locale

class TransitousRoutingRepository(
    private val client: HttpClient,
    private val language: String = Locale.getDefault().toLanguageTag(),
) : RoutingRepository {
    private data class SearchKey(val query: String, val bias: String?, val language: String)
    private val cache = object : LinkedHashMap<SearchKey, List<Location>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchKey, List<Location>>) = size > 64
    }

    override suspend fun searchLocations(query: String, near: GeoPoint?): List<Location> {
        val text = query.trim()
        if (text.isBlank()) return emptyList()
        val bias = near?.rounded(2)
        val key = SearchKey(text.lowercase(Locale.ROOT), bias, language)
        synchronized(cache) { cache[key] }?.let { return it }
        return mapped {
            val results = get<List<Match>>("v1/geocode") {
                parameter("text", text)
                parameter("numResults", 10)
                parameter("language", language)
                bias?.let { parameter("place", it) }
            }.map { it.toDomain() }
            synchronized(cache) { cache[key] = results }
            results
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): Location? = mapped {
        get<List<Match>>("v1/reverse-geocode") {
            parameter("place", point.coordinates())
            parameter("numResults", 1)
        }.firstOrNull()?.toDomain()
    }

    override suspend fun planJourneys(request: JourneyRequest, cursor: String?): JourneyPage = mapped {
        get<PlanResponse>("v6/plan") {
            parameter("fromPlace", request.from.stopId ?: request.from.point.coordinates())
            parameter("toPlace", request.to.stopId ?: request.to.point.coordinates())
            request.time?.let { parameter("time", it.toString()) }
            parameter("arriveBy", request.arriveBy)
            cursor?.let { parameter("pageCursor", it) }
            parameter("detailedLegs", true)
            parameter("detailedTransfers", false)
            parameter("language", language)
        }.toDomain()
    }

    override suspend fun nearbyDepartures(point: GeoPoint, radiusMeters: Int, limit: Int): List<Departure> = mapped {
        get<StoptimesResponse>("v6/stoptimes") {
            parameter("center", point.rounded(4))
            parameter("radius", radiusMeters)
            parameter("exactRadius", true)
            parameter("n", limit)
            parameter("withAlerts", false)
            parameter("language", language)
        }.stopTimes.map { it.toDomain() }.sortedBy { it.time.best }
    }

    override suspend fun lineDepartures(
        stopId: String,
        line: Line,
        directionId: String?,
        from: Instant,
        limit: Int,
    ): List<Departure> = mapped {
        get<StoptimesResponse>("v6/stoptimes") {
            parameter("stopId", stopId)
            parameter("time", from.toString())
            parameter("n", LINE_SCAN)
            parameter("withAlerts", false)
            parameter("language", language)
        }.stopTimes.map { it.toDomain() }
            .filter { it.line.sameAs(line) && (directionId == null || it.directionId == directionId) }
            .sortedBy { it.time.best }
            .take(limit)
    }

    override suspend fun stopsIn(box: BoundingBox): List<Location> = mapped {
        get<List<Place>>("v6/map/stops") {
            // The spec uses lower right / upper left, confirmed by the fixture request:
            // min=52.518,13.418&max=52.525,13.408. Longitude order is east, then west.
            parameter("min", "${box.south},${box.east}")
            parameter("max", "${box.north},${box.west}")
        }.map { it.toLocation() }
    }

    private suspend inline fun <reified T> get(path: String, noinline parameters: HttpRequestBuilder.() -> Unit): T {
        val response = client.get(path, parameters)
        if (!response.status.isSuccess()) throw RoutingException.Server(response.status.value)
        return response.body()
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RoutingException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw RoutingException.Offline(e)
    } catch (e: ConnectTimeoutException) {
        throw RoutingException.Offline(e)
    } catch (e: SocketTimeoutException) {
        throw RoutingException.Offline(e)
    } catch (e: IOException) {
        throw RoutingException.Offline(e)
    } catch (e: Exception) {
        throw RoutingException.Unexpected(e)
    }
}

/**
 * The API has no line filter, so a line's departures come from a scan of the stop's next
 * departures. A stop with many lines can give fewer than the requested number.
 */
private const val LINE_SCAN = 40

private fun Line.sameAs(other: Line) = if (id != null && other.id != null) id == other.id else name == other.name

private fun GeoPoint.coordinates() = "$latitude,$longitude"
private fun GeoPoint.rounded(decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f,%.${decimals}f", latitude, longitude)
