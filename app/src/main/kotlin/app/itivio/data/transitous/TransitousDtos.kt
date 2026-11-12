package app.itivio.data.transitous

import kotlinx.serialization.Serializable

@Serializable
internal data class Match(
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val areas: List<Area> = emptyList(),
    val modes: List<String> = emptyList(),
    val country: String? = null,
)

@Serializable
internal data class Area(
    val name: String,
    val default: Boolean = false,
    val unique: Boolean = false,
)

@Serializable
internal data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val parentId: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val tz: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val arrivalTimestampReason: String? = null,
    val departureTimestampReason: String? = null,
    val cancelled: Boolean = false,
    val modes: List<String> = emptyList(),
)

@Serializable
internal data class Leg(
    val mode: String,
    val from: Place,
    val to: Place,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val realTime: Boolean = false,
    val distance: Double? = null,
    val legGeometry: EncodedPolyline? = null,
    val intermediateStops: List<Place> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val routeShortName: String? = null,
    val displayName: String? = null,
    val tripShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val routeId: String? = null,
    val directionId: String? = null,
    val tripId: String? = null,
    val headsign: String? = null,
    val tripTo: Place? = null,
    val cancelled: Boolean = false,
)

@Serializable
internal data class Itinerary(
    val legs: List<Leg>,
    val id: String? = null,
    val transfers: Int = 0,
)

@Serializable
internal data class EncodedPolyline(
    val points: String,
    val precision: Int,
)

@Serializable
internal data class StopTime(
    val place: Place,
    val mode: String,
    val realTime: Boolean = false,
    val tripCancelled: Boolean = false,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val tripShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val routeId: String? = null,
    val directionId: String? = null,
    val tripId: String? = null,
    val headsign: String? = null,
    val tripTo: Place? = null,
    /** Stops after this one on the trip. Set only when the request asks for them with `fetchStops`. */
    val nextStops: List<Place>? = null,
    val cancelled: Boolean = false,
)

@Serializable
internal data class Alert(
    val headerText: String,
    val descriptionText: String,
    val url: String? = null,
)

@Serializable
internal data class PlanResponse(
    val itineraries: List<Itinerary>,
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Serializable
internal data class StoptimesResponse(
    val stopTimes: List<StopTime>,
)
