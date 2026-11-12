package app.itivio.data.transitous

import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyPage
import app.itivio.domain.Line
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.TransitMode
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import app.itivio.domain.Leg as DomainLeg
import app.itivio.domain.Stop as DomainStop
import app.itivio.domain.StopTime as DomainTime

internal fun transitMode(mode: String): TransitMode = when (mode) {
    "WALK" -> TransitMode.WALK
    "BIKE", "RENTAL" -> TransitMode.BIKE
    "CAR", "CAR_PARKING", "CAR_DROPOFF" -> TransitMode.CAR
    "BUS" -> TransitMode.BUS
    "COACH" -> TransitMode.COACH
    "TRAM" -> TransitMode.TRAM
    "SUBWAY", "METRO" -> TransitMode.SUBWAY
    "SUBURBAN" -> TransitMode.SUBURBAN
    "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> TransitMode.RAIL
    "HIGHSPEED_RAIL" -> TransitMode.HIGHSPEED_RAIL
    "FERRY" -> TransitMode.FERRY
    "AIRPLANE" -> TransitMode.AIRPLANE
    "AERIAL_LIFT", "AREAL_LIFT", "CABLE_CAR" -> TransitMode.AERIAL_LIFT
    "FUNICULAR" -> TransitMode.FUNICULAR
    "ODM", "FLEX", "RIDE_SHARING" -> TransitMode.ON_DEMAND
    else -> TransitMode.OTHER
}

internal fun parseColor(hex: String?): Int? = hex
    ?.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
    ?.toInt(16)?.or(0xFF000000.toInt())

private fun line(short: String?, display: String?, trip: String?, long: String?, color: String?, text: String?, agency: String?, id: String?) =
    Line(listOfNotNull(short, display, trip, long).firstOrNull { it.isNotBlank() } ?: "",
        long?.takeIf { it.isNotBlank() }, parseColor(color), parseColor(text), agency, id)

internal fun Match.toDomain(): Location {
    val default = areas.firstOrNull { it.default && it.name != name }?.name
    val unique = areas.firstOrNull { it.unique && it.name != default && it.name != name }?.name
    val area = default?.let { if (unique != null) "$unique, $it" else it }
    val kind = when (type) { "STOP" -> LocationKind.STOP; "ADDRESS" -> LocationKind.ADDRESS; else -> LocationKind.PLACE }
    return Location(id, name, kind, GeoPoint(lat, lon), area, id.takeIf { kind == LocationKind.STOP }, modes.map(::transitMode).toSet())
}

internal fun Place.toLocation() = Location(
    stopId ?: "$lat,$lon", name, LocationKind.STOP, GeoPoint(lat, lon),
    stopId = stopId, modes = modes.map(::transitMode).toSet(),
)

private fun realtime(reason: String?) = reason in setOf("OBSERVED", "PROPAGATED", "PREDICTED")
private fun time(scheduled: String?, current: String?, live: Boolean): DomainTime? {
    val baseline = scheduled ?: current ?: return null
    return DomainTime(Instant.parse(baseline), current?.takeIf { live }?.let(Instant::parse))
}

/** [coordinateName] replaces the name MOTIS gives a coordinate endpoint, such as "START" or "END". */
internal fun Place.toDomain(coordinateName: String? = null): DomainStop = DomainStop(
    coordinateName?.takeIf { stopId == null } ?: name, GeoPoint(lat, lon), stopId, track ?: scheduledTrack,
    time(scheduledArrival, arrival, realtime(arrivalTimestampReason)),
    time(scheduledDeparture, departure, realtime(departureTimestampReason)), cancelled,
    tz?.let { try { ZoneId.of(it) } catch (_: DateTimeException) { null } },
    stationId = parentId,
)

/**
 * Some feeds, such as the RER in Île-de-France, put the mission code in the headsign: "QOKA" for
 * trip "QOKA52". The headsign then names no place, so the trip's final stop replaces it.
 */
private class Heading(headsign: String?, tripShortName: String?, tripTo: Place?) {
    private val code = !headsign.isNullOrBlank() && !tripShortName.isNullOrBlank() && tripShortName.startsWith(headsign)
    val headsign: String? = if (code) tripTo?.name ?: headsign else headsign?.takeIf { it.isNotBlank() } ?: tripTo?.name
    val tripCode: String? = tripShortName.takeIf { code }
}

internal fun Leg.toDomain(fromName: String? = null, toName: String? = null): DomainLeg {
    val mode = transitMode(mode)
    val start = requireNotNull(time(scheduledStartTime, startTime, realTime))
    val end = requireNotNull(time(scheduledEndTime, endTime, realTime))
    val geometry = legGeometry?.let { decodePolyline(it.points, it.precision) } ?: emptyList()
    val from = from.toDomain(fromName)
    val to = to.toDomain(toName)
    if (!mode.isTransit) return DomainLeg.Street(mode, from, to, start, end, geometry, distance ?: 0.0)
    val heading = Heading(headsign, tripShortName, tripTo)
    return DomainLeg.Transit(mode, from, to, start, end, geometry,
        line(routeShortName, displayName, tripShortName, routeLongName, routeColor, routeTextColor, agencyName, routeId),
        heading.headsign, tripId, intermediateStops.map { it.toDomain() }, cancelled,
        alerts.map { app.itivio.domain.Alert(it.headerText, it.descriptionText, it.url) }, directionId, heading.tripCode)
}

/** [fromName] and [toName] name the requested endpoints; see [Place.toDomain]. */
internal fun Itinerary.toDomain(fromName: String? = null, toName: String? = null): Journey {
    val mapped = legs.mapIndexed { index, leg ->
        leg.toDomain(fromName.takeIf { index == 0 }, toName.takeIf { index == legs.lastIndex })
    }
    val stableId = id ?: MessageDigest.getInstance("SHA-256").digest(
        legs.joinToString("|") { "${it.tripId}:${it.startTime}:${it.endTime}" }.toByteArray(Charsets.UTF_8)
    ).joinToString("") { "%02x".format(it) }
    return Journey(stableId, mapped, mapped.first().start, mapped.last().end, transfers)
}

internal fun PlanResponse.toDomain(fromName: String? = null, toName: String? = null) = JourneyPage(
    itineraries.filter { it.legs.isNotEmpty() }.map { it.toDomain(fromName, toName) }, previousPageCursor, nextPageCursor,
)

internal fun StopTime.toDomain(): Departure {
    val heading = Heading(headsign, tripShortName, tripTo)
    return Departure(
        place.toDomain(), line(routeShortName, displayName, tripShortName, routeLongName, routeColor, routeTextColor, agencyName, routeId),
        // A trip's last stop has only an arrival; fall back to it instead of failing the whole list.
        transitMode(mode), heading.headsign,
        requireNotNull(
            time(place.scheduledDeparture, place.departure, realTime)
                ?: time(place.scheduledArrival, place.arrival, realTime),
        ),
        cancelled || tripCancelled || place.cancelled, tripId, directionId, heading.tripCode,
    )
}
