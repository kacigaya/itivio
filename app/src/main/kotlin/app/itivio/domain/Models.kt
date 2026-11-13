package app.itivio.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class GeoPoint(val latitude: Double, val longitude: Double)

data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)

enum class LocationKind { ADDRESS, PLACE, STOP }

/** A searchable place: an address, a point of interest, or a transit stop. */
data class Location(
    val id: String,
    val name: String,
    val kind: LocationKind,
    val point: GeoPoint,
    /** Human-readable area, such as "Mitte, Berlin". Null when the provider gives none. */
    val area: String? = null,
    /** Provider stop ID. Set only for transit stops; routing prefers it over coordinates. */
    val stopId: String? = null,
    val modes: Set<TransitMode> = emptySet(),
)

enum class TransitMode {
    WALK, BIKE, CAR,
    BUS, COACH, TRAM, SUBWAY, SUBURBAN, RAIL, HIGHSPEED_RAIL, FERRY, AIRPLANE,
    AERIAL_LIFT, FUNICULAR, ON_DEMAND, OTHER;

    val isTransit: Boolean get() = this != WALK && this != BIKE && this != CAR
}

/**
 * A time at a stop. [estimated] is set only when real-time data exists for this event, so the UI
 * can tell scheduled from live information. Coverage varies by region; never assume it is present.
 */
data class StopTime(val scheduled: Instant, val estimated: Instant? = null) {
    val isRealtime: Boolean get() = estimated != null
    val best: Instant get() = estimated ?: scheduled
    val delay: Duration? get() = estimated?.let { Duration.between(scheduled, it) }
}

data class Stop(
    val name: String,
    val point: GeoPoint,
    val stopId: String? = null,
    /** Platform or track, real-time value when available. */
    val track: String? = null,
    val arrival: StopTime? = null,
    val departure: StopTime? = null,
    val cancelled: Boolean = false,
    val zone: ZoneId? = null,
    /** Parent station of [stopId], when the provider gives one. */
    val stationId: String? = null,
)

data class Line(
    /** Short label shown on badges, such as "M41" or "U2". */
    val name: String,
    val longName: String? = null,
    /** Agency colors as ARGB. Null when the feed has none; the UI then uses theme colors. */
    val color: Int? = null,
    val textColor: Int? = null,
    val agency: String? = null,
    /** Provider route ID. Null when the provider gives none; lines then match by [name]. */
    val id: String? = null,
)

data class Alert(val header: String, val description: String, val url: String? = null)

sealed interface Leg {
    val mode: TransitMode
    val from: Stop
    val to: Stop
    val start: StopTime
    val end: StopTime
    /** Decoded route geometry. May be empty when the provider omits it. */
    val geometry: List<GeoPoint>
    val duration: Duration get() = Duration.between(start.best, end.best)

    /** Walking, cycling, or driving between places. */
    data class Street(
        override val mode: TransitMode,
        override val from: Stop,
        override val to: Stop,
        override val start: StopTime,
        override val end: StopTime,
        override val geometry: List<GeoPoint>,
        val distanceMeters: Double,
        /** Turn-by-turn steps of a walk. Empty when the provider gives none. */
        val steps: List<WalkStep> = emptyList(),
    ) : Leg

    data class Transit(
        override val mode: TransitMode,
        override val from: Stop,
        override val to: Stop,
        override val start: StopTime,
        override val end: StopTime,
        override val geometry: List<GeoPoint>,
        val line: Line,
        val headsign: String?,
        val tripId: String?,
        val intermediateStops: List<Stop>,
        val cancelled: Boolean = false,
        val alerts: List<Alert> = emptyList(),
        /** Direction of travel on the line, as the provider names it. */
        val directionId: String? = null,
        /** See [Departure.tripCode]. */
        val tripCode: String? = null,
    ) : Leg
}

data class Journey(
    val id: String,
    val legs: List<Leg>,
    val departure: StopTime,
    val arrival: StopTime,
    val transfers: Int,
) {
    val duration: Duration get() = Duration.between(departure.best, arrival.best)
    val walkingMeters: Double
        get() = legs.filterIsInstance<Leg.Street>().filter { it.mode == TransitMode.WALK }.sumOf { it.distanceMeters }
    val hasRealtime: Boolean get() = legs.any { it is Leg.Transit && (it.start.isRealtime || it.end.isRealtime) }
    val isCancelled: Boolean get() = legs.any { it is Leg.Transit && it.cancelled }

    /**
     * Index of the leg in progress at [now], by the leg times. A wait between two legs counts for
     * the next leg. Null before the departure and from the arrival on.
     */
    fun currentLegIndex(now: Instant): Int? =
        if (now < departure.best || now >= arrival.best) null else legs.indexOfFirst { it.end.best > now }.takeIf { it >= 0 }

    /** True when the journey has no transit leg, such as a walk the whole way. */
    val isStreetOnly: Boolean get() = legs.none { it is Leg.Transit }

    /** The service alerts of all transit legs. An alert on more than one leg shows once. */
    val alerts: List<Alert> get() = legs.filterIsInstance<Leg.Transit>().flatMap { it.alerts }.distinct()

    /**
     * Applies the current times of the trip of the transit leg at [legIndex]. The boarding and
     * alighting stops keep their place in the trip; the walks after the leg move with its arrival.
     * The journey stays the same when the trip does not contain the stops of the leg.
     */
    fun withTrip(legIndex: Int, trip: Trip): Journey {
        val leg = legs.getOrNull(legIndex) as? Leg.Transit ?: return this
        val board = trip.stops.indices.firstOrNull { trip.stops[it].isBoardingOf(leg) }
            ?: trip.stops.indexOfFirst { it.stopId != null && it.stopId == leg.from.stopId }.takeIf { it >= 0 }
            ?: return this
        val alight = (board + 1 until trip.stops.size).firstOrNull { trip.stops[it].isAlightingOf(leg) }
            ?: (board + 1 until trip.stops.size).firstOrNull { trip.stops[it].stopId != null && trip.stops[it].stopId == leg.to.stopId }
            ?: return this
        val from = trip.stops[board]
        val to = trip.stops[alight]
        val updated = leg.copy(
            from = leg.from.copy(departure = from.departure ?: leg.from.departure, track = from.track ?: leg.from.track, cancelled = from.cancelled),
            to = leg.to.copy(arrival = to.arrival ?: leg.to.arrival, track = to.track ?: leg.to.track, cancelled = to.cancelled),
            start = from.departure ?: leg.start,
            end = to.arrival ?: leg.end,
            intermediateStops = trip.stops.subList(board + 1, alight),
            cancelled = trip.cancelled || from.cancelled || to.cancelled,
            alerts = trip.alerts,
        )
        val shift = Duration.between(leg.end.best, updated.end.best)
        val next = (legIndex + 1 until legs.size).firstOrNull { legs[it] is Leg.Transit } ?: legs.size
        val newLegs = legs.mapIndexed { index, it ->
            when {
                index == legIndex -> updated
                index in legIndex + 1 until next && it is Leg.Street -> it.copy(start = it.start.shifted(shift), end = it.end.shifted(shift))
                else -> it
            }
        }
        return copy(legs = newLegs, departure = newLegs.first().start, arrival = newLegs.last().end)
    }
}

private fun Stop.isBoardingOf(leg: Leg.Transit) =
    stopId != null && stopId == leg.from.stopId && departure?.scheduled == leg.start.scheduled

private fun Stop.isAlightingOf(leg: Leg.Transit) =
    stopId != null && stopId == leg.to.stopId && arrival?.scheduled == leg.end.scheduled

/** Moves the expected time by [shift]. The scheduled time stays. */
private fun StopTime.shifted(shift: Duration): StopTime =
    if (shift.isZero) this else copy(estimated = best.plus(shift))

/**
 * The current state of one trip, from its first stop to its last. [stops] carry the live times
 * where the provider has them.
 */
data class Trip(val stops: List<Stop>, val cancelled: Boolean = false, val alerts: List<Alert> = emptyList())

enum class WalkingSpeed { SLOW, NORMAL, FAST }

/** How the user wants to travel. The defaults put no limit on the routing. */
data class JourneyOptions(
    /** Transit modes the user turned off. An empty set allows all modes. */
    val excludedModes: Set<TransitMode> = emptySet(),
    /** The most transfers a journey can have. Null means no limit. */
    val maxTransfers: Int? = null,
    /** Keeps walks and transfers on step-free paths, where the provider has the data. */
    val wheelchair: Boolean = false,
    /** Not used with [wheelchair]: the provider then applies its wheelchair speed. */
    val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
) {
    val isDefault: Boolean get() = this == JourneyOptions()

    /** Turns [mode] on or off. The last selectable mode stays on, so a journey can always use transit. */
    fun withMode(mode: TransitMode, allowed: Boolean): JourneyOptions {
        val excluded = if (allowed) excludedModes - mode else excludedModes + mode
        return if (excluded.containsAll(SELECTABLE_MODES)) this else copy(excludedModes = excluded)
    }

    companion object {
        /**
         * The modes the user can turn off. The rare modes (cable car, funicular, on-demand, and
         * other) have no switch and stay on.
         */
        val SELECTABLE_MODES: List<TransitMode> = listOf(
            TransitMode.BUS, TransitMode.COACH, TransitMode.TRAM, TransitMode.SUBWAY, TransitMode.SUBURBAN,
            TransitMode.RAIL, TransitMode.HIGHSPEED_RAIL, TransitMode.FERRY, TransitMode.AIRPLANE,
        )

        /** The highest transfer limit the user can set. */
        const val MAX_TRANSFER_LIMIT = 3
    }
}

data class JourneyRequest(
    val from: Location,
    val to: Location,
    /** Null means "now". */
    val time: Instant? = null,
    val arriveBy: Boolean = false,
    val options: JourneyOptions = JourneyOptions(),
)

data class JourneyPage(
    val journeys: List<Journey>,
    /** Opaque cursors; pass to [RoutingRepository.planJourneys] to page earlier or later. */
    val earlierCursor: String?,
    val laterCursor: String?,
)

data class Departure(
    val stop: Stop,
    val line: Line,
    val mode: TransitMode,
    val headsign: String?,
    val time: StopTime,
    val cancelled: Boolean,
    val tripId: String?,
    val directionId: String? = null,
    /**
     * Trip code that riders see on platform boards, such as the RER mission "QOKA52". Set only
     * when the feed put the code in place of the headsign.
     */
    val tripCode: String? = null,
)
