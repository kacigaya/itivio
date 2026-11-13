package app.itivio.data.transitous

import app.itivio.domain.BoundingBox
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyPage
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Line
import app.itivio.domain.Location
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import app.itivio.domain.Stop
import app.itivio.domain.TransitMode
import app.itivio.domain.Trip
import app.itivio.domain.WalkingSpeed
import app.itivio.data.ign.IgnAddressSearch
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
    // Read for each request: the user can change the app language while the app runs.
    private val language: () -> String = { Locale.getDefault().toLanguageTag() },
    private val frenchAddresses: IgnAddressSearch? = null,
) : RoutingRepository {
    private data class SearchKey(val query: String, val bias: String?, val language: String)
    private val cache = object : LinkedHashMap<SearchKey, List<Location>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchKey, List<Location>>) = size > 64
    }

    override suspend fun searchLocations(query: String, near: GeoPoint?): List<Location> {
        val text = query.trim()
        if (text.isBlank()) return emptyList()
        val bias = near?.rounded(2)
        val language = language()
        val key = SearchKey(text.lowercase(Locale.ROOT), bias, language)
        synchronized(cache) { cache[key] }?.let { return it }
        return mapped {
            val matches = get<List<Match>>("v1/geocode") {
                parameter("text", text)
                parameter("numResults", 10)
                parameter("language", language())
                bias?.let { parameter("place", it) }
            }
            var complete = true
            val addresses = if (frenchAddresses != null && matches.firstOrNull()?.country == "FR" &&
                IgnAddressSearch.looksLikeAddress(text)
            ) {
                try {
                    frenchAddresses.search(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep transport suggestions available and allow a later retry of IGN.
                    complete = false
                    emptyList()
                }
            } else emptyList()
            val results = (addresses + matches.map { it.toDomain() }).fold(emptyList<Location>()) { kept, next ->
                if (next.kind != app.itivio.domain.LocationKind.STOP && kept.any {
                    it.kind == next.kind && it.name.equals(next.name, ignoreCase = true) &&
                        kotlin.math.abs(it.point.latitude - next.point.latitude) < 0.0002 &&
                        kotlin.math.abs(it.point.longitude - next.point.longitude) < 0.0002
                }) kept else kept + next
            }.take(10)
            if (complete) synchronized(cache) { cache[key] = results }
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
            val options = request.options
            if (options.excludedModes.isNotEmpty()) parameter("transitModes", options.transitModes())
            options.maxTransfers?.let { parameter("maxTransfers", it) }
            if (options.wheelchair) {
                parameter("pedestrianProfile", "WHEELCHAIR")
                // Without routed transfers, MOTIS applies the profile only to the first and last walk.
                parameter("useRoutedTransfers", true)
            } else {
                options.walkingSpeed.metersPerSecond()?.let { parameter("pedestrianSpeed", it) }
            }
            parameter("detailedLegs", true)
            parameter("detailedTransfers", false)
            parameter("language", language())
        }.toDomain(request.from.name, request.to.name)
    }

    override suspend fun nearbyDepartures(point: GeoPoint, radiusMeters: Int, limit: Int, withAlerts: Boolean): List<Departure> = mapped {
        get<StoptimesResponse>("v6/stoptimes") {
            parameter("center", point.rounded(4))
            parameter("radius", radiusMeters)
            parameter("exactRadius", true)
            parameter("n", limit)
            parameter("withAlerts", withAlerts)
            parameter("language", language())
        }.stopTimes.map { it.toDomain() }.sortedBy { it.time.best }
    }

    override suspend fun stopDepartures(stopId: String, limit: Int, withAlerts: Boolean): List<Departure> = mapped {
        get<StoptimesResponse>("v6/stoptimes") {
            parameter("stopId", stopId)
            parameter("n", limit)
            parameter("withAlerts", withAlerts)
            parameter("language", language())
        }.stopTimes.map { it.toDomain() }.sortedBy { it.time.best }
    }

    override suspend fun trip(tripId: String): Trip = mapped {
        get<Itinerary>("v6/trip") {
            parameter("tripId", tripId)
            // The tracker needs the times, not the shape of the route.
            parameter("detailedLegs", false)
            parameter("language", language())
        }.toTrip()
    }

    override suspend fun lineDepartures(
        stopId: String,
        line: Line,
        directionId: String?,
        from: Instant,
        limit: Int,
        alightAt: Stop?,
    ): List<Departure> = mapped {
        val target = alightAt?.takeIf { it.stopId != null || it.stationId != null }
        if (limit <= 0) return@mapped emptyList()
        val collected = mutableListOf<StopTime>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        var departures = emptyList<Departure>()
        repeat(LINE_MAX_PAGES) {
            val page = try {
                mapped { get<StoptimesResponse>("v6/stoptimes") {
                    parameter("stopId", stopId)
                    parameter("time", from.toString())
                    parameter("n", LINE_SCAN)
                    parameter("withAlerts", false)
                    parameter("language", language())
                    if (target != null) parameter("fetchStops", true)
                    cursor?.let { parameter("pageCursor", it) }
                } }
            } catch (error: RoutingException) {
                if (departures.isEmpty()) throw error
                return@mapped departures.take(limit)
            }
            collected += page.stopTimes
            val sameLine = collected.map { it to it.toDomain() }.filter { (_, departure) -> departure.line.sameAs(line) }
            // IDFM can use the same direction ID in both directions. Prefer actual next stops.
            val byStops = target?.takeIf { sameLine.any { (dto, _) -> dto.nextStops != null } }
            departures = sameLine.filter { (dto, departure) ->
                if (byStops != null) dto.nextStops.orEmpty().any { it.isAt(byStops) && (!it.cancelled || departure.cancelled) }
                else directionId == null || departure.directionId == directionId
            }.map { it.second }
                .filter { it.time.best >= from }
                .distinctBy { Triple(it.tripId, it.stop.stopId, it.time.scheduled) }
                .sortedBy { it.time.best }
            val next = page.nextPageCursor?.takeIf { it.isNotBlank() }
            if (departures.size >= limit || page.stopTimes.isEmpty() || next == null || !cursors.add(next)) {
                return@mapped departures.take(limit)
            }
            cursor = next
        }
        departures.take(limit)
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

// Bound each refresh at busy stations and on lines with little service.
private const val LINE_SCAN = 80
private const val LINE_MAX_PAGES = 8

/** A trip can stop at another platform of the same station as the planned one. */
private fun Place.isAt(stop: Stop) =
    (stop.stopId != null && stopId == stop.stopId) || (stop.stationId != null && parentId == stop.stationId)

/** The MOTIS modes that stay allowed. MOTIS has no parameter that excludes a mode. */
private fun JourneyOptions.transitModes() = TransitMode.entries
    .filter { it.isTransit && it !in excludedModes }
    .flatMap { it.motisModes() }
    .joinToString(",")

/** The reverse of [transitMode], without the deprecated MOTIS names. */
private fun TransitMode.motisModes(): List<String> = when (this) {
    TransitMode.WALK, TransitMode.BIKE, TransitMode.CAR -> emptyList()
    TransitMode.RAIL -> listOf("REGIONAL_RAIL", "LONG_DISTANCE", "NIGHT_RAIL")
    TransitMode.ON_DEMAND -> listOf("ODM", "RIDE_SHARING")
    else -> listOf(name)
}

/** Null leaves the MOTIS default of 1.2 m/s. MOTIS accepts speeds between 0.3 and 10 m/s. */
private fun WalkingSpeed.metersPerSecond(): Double? = when (this) {
    WalkingSpeed.SLOW -> 0.9
    WalkingSpeed.NORMAL -> null
    WalkingSpeed.FAST -> 1.5
}

private fun Line.sameAs(other: Line) = if (id != null && other.id != null) id == other.id else name == other.name

private fun GeoPoint.coordinates() = "$latitude,$longitude"
private fun GeoPoint.rounded(decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f,%.${decimals}f", latitude, longitude)
