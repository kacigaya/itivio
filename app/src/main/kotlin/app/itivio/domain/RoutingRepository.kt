package app.itivio.domain

/**
 * Provider-neutral access to search, routing, and departures. The app ships with a Transitous
 * (MOTIS 2) implementation; a self-hosted MOTIS instance or another provider can replace it
 * without UI changes.
 *
 * Every function is main-safe and cancellable. Failures surface as [RoutingException].
 */
interface RoutingRepository {
    /** Autocomplete for [query]. [near] biases ranking; implementations may coarsen it. */
    suspend fun searchLocations(query: String, near: GeoPoint? = null): List<Location>

    /** Nearest named place for a coordinate, used for "My location" labels. */
    suspend fun reverseGeocode(point: GeoPoint): Location?

    /** Plans journeys. Pass a cursor from a previous [JourneyPage] to page in time. */
    suspend fun planJourneys(request: JourneyRequest, cursor: String? = null): JourneyPage

    /** Upcoming departures around [point], sorted by time. */
    suspend fun nearbyDepartures(point: GeoPoint, radiusMeters: Int = 400, limit: Int = 20): List<Departure>

    /** Transit stops inside [box], for map display. */
    suspend fun stopsIn(box: BoundingBox): List<Location>
}

sealed class RoutingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connectivity or the host was unreachable. */
    class Offline(cause: Throwable? = null) : RoutingException("Network unavailable", cause)

    /** The service answered with an error status. */
    class Server(val status: Int) : RoutingException("Server error $status")

    /** The service returned data the app could not read. */
    class Unexpected(cause: Throwable? = null) : RoutingException("Unexpected response", cause)
}
