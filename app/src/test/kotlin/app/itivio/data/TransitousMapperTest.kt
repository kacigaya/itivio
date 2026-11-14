package app.itivio.data

import app.itivio.data.transitous.*
import app.itivio.domain.AlertCause
import app.itivio.domain.AlertEffect
import app.itivio.domain.AlertSeverity
import app.itivio.domain.Leg as DomainLeg
import app.itivio.domain.TimeRange as DomainRange
import app.itivio.domain.TransitMode
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TransitousMapperTest {
    private inline fun <reified T> fixture(name: String): T = transitousJson.decodeFromString(
        requireNotNull(javaClass.getResource("/transitous/$name.json")).readText(),
    )

    @Test fun transilienMissionNamesAreCodesRatherThanDestinations() {
        val source = fixture<StoptimesResponse>("stoptimes-idfm").stopTimes[1]
        val departure = source.copy(agencyName = "Transilien", headsign = "GAMO", tripShortName = "151071").toDomain()
        assertEquals("GAMO", departure.tripCode)
        assertEquals(source.tripTo?.name, departure.headsign)
        val otherAgency = source.copy(agencyName = "Other", headsign = "GAMO", tripShortName = "151071").toDomain()
        assertNull(otherAgency.tripCode)
        assertEquals("GAMO", otherAgency.headsign)
    }

    @Test fun arrivalAtTheAlightingStopComesFromTheNextStops() {
        val source = fixture<StoptimesResponse>("stoptimes-idfm").stopTimes.first { !it.nextStops.isNullOrEmpty() }
        val stops = requireNotNull(source.nextStops)
        val target = stops.last()
        val timed = source.copy(
            realTime = true,
            nextStops = stops.map {
                if (it === target) it.copy(scheduledArrival = "2026-11-12T18:00:00Z", arrival = "2026-11-12T18:04:00Z") else it
            },
        )
        val arrival = requireNotNull(timed.arrivalAt { it.stopId == target.stopId })
        assertEquals(Instant.parse("2026-11-12T18:00:00Z"), arrival.scheduled)
        assertEquals(Instant.parse("2026-11-12T18:04:00Z"), arrival.estimated)
        // Without live data, the planned time is the only time.
        assertNull(timed.copy(realTime = false).arrivalAt { it.stopId == target.stopId }?.estimated)
        // The stops of an older server have no times, and a request without the stops has no list.
        assertNull(source.arrivalAt { it.stopId == target.stopId })
        assertNull(source.copy(nextStops = null).arrivalAt { true })
    }

    @Test fun googlePolyline() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, points.size)
        val expected = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        expected.zip(points).forEach { (pair, point) ->
            assertEquals(pair.first, point.latitude, 0.000001)
            assertEquals(pair.second, point.longitude, 0.000001)
        }
    }

    @Test fun precisionSixFixture() {
        val leg = fixture<PlanResponse>("plan").itineraries.first().legs.first()
        val geometry = requireNotNull(leg.legGeometry)
        assertEquals(6, geometry.precision)
        val points = decodePolyline(geometry.points, geometry.precision)
        assertEquals(leg.from.lat, points.first().latitude, 0.0002)
        assertEquals(leg.from.lon, points.first().longitude, 0.0002)
        assertEquals(leg.to.lat, points.last().latitude, 0.0002)
        assertEquals(leg.to.lon, points.last().longitude, 0.0002)
    }

    @Test fun malformedPolylineIsRejected() {
        listOf("_", "?", "!!!!!!!!", "~~~~~~~~~~~~").forEach {
            assertThrows(IllegalArgumentException::class.java) { decodePolyline(it, 6) }
        }
    }

    @Test fun planFixture() {
        val dto = fixture<PlanResponse>("plan")
        val page = dto.toDomain()
        assertEquals(3, page.journeys.size)
        assertEquals(listOf(0, 1, 0), page.journeys.map { it.transfers })
        assertTrue(page.journeys.all { it.walkingMeters > 0 })
        val transit = page.journeys.flatMap { it.legs }.filterIsInstance<DomainLeg.Transit>()
        assertEquals(listOf("M41", "M41", "N2", "N6"), transit.map { it.line.name })
        assertTrue(transit.all { it.mode == TransitMode.BUS })
        transit.forEach {
            if (it.line.name == "M41") {
                assertNull(it.start.estimated)
                assertNull(it.end.estimated)
            } else {
                assertNotNull(it.start.estimated)
                assertNotNull(it.end.estimated)
            }
        }
        // The fixture plans between coordinates, so MOTIS names the ends "START" and "END".
        val named = dto.toDomain(fromName = "Home", toName = "Work").journeys.first()
        assertEquals("Home", named.legs.first().from.name)
        assertEquals("Work", named.legs.last().to.name)
        assertTrue(named.legs.dropLast(1).none { it.to.name == "Work" })
        assertEquals(dto.previousPageCursor, page.earlierCursor)
        assertEquals(dto.nextPageCursor, page.laterCursor)
        assertEquals(dto.itineraries.first().id, page.journeys.first().id)
        val noId = dto.itineraries.first().copy(id = null)
        assertEquals(noId.toDomain().id, noId.toDomain().id)
        assertNotEquals(noId.toDomain().id, noId.copy(legs = noId.legs.drop(1)).toDomain().id)
        assertTrue(dto.copy(itineraries = listOf(noId.copy(legs = emptyList()))).toDomain().journeys.isEmpty())
    }

    @Test fun geocodeAndAreas() {
        val matches = fixture<List<Match>>("geocode")
        assertEquals("Berlin", matches.first().toDomain().area)
        assertEquals(matches.first().id, matches.first().toDomain().stopId)
        assertNull(matches[2].toDomain().stopId)
        val match = matches.first().copy(name = "Station", areas = listOf(
            Area("Berlin", default = true), Area("Mitte", unique = true),
        ))
        assertEquals("Mitte, Berlin", match.toDomain().area)
        assertEquals("Berlin", match.copy(name = "Mitte").toDomain().area)
        assertNull(match.copy(areas = emptyList()).toDomain().area)
        assertEquals("Berlin", fixture<List<Match>>("reverse-geocode").first().toDomain().area)
        val exponent = transitousJson.decodeFromString<Match>(
            """{"type":"STOP","name":"Test","id":"x","lat":5.25221252E1,"lon":2E0}""",
        )
        assertEquals(52.5221252, exponent.lat, 0.0000001)
        assertEquals(2.0, exponent.lon, 0.0)
    }

    @Test fun departuresAndPlaceRealtime() {
        val dto = fixture<StoptimesResponse>("stoptimes").stopTimes.first()
        assertEquals("PREDICTED", dto.place.departureTimestampReason)
        assertNotNull(dto.toDomain().time.estimated)
        assertNull(dto.copy(realTime = false).toDomain().time.estimated)
        for (reason in listOf(null, "NO_RT_DATA", "INCONSISTENT_RT", "UNKNOWN")) {
            assertNull(dto.place.copy(departureTimestampReason = reason).toDomain().departure?.estimated)
        }
        for (reason in listOf("OBSERVED", "PROPAGATED", "PREDICTED")) {
            assertNotNull(dto.place.copy(departureTimestampReason = reason).toDomain().departure?.estimated)
        }
        assertEquals(ZoneId.of("Europe/Berlin"), dto.place.toDomain().zone)
        assertNull(dto.place.copy(tz = "invalid/zone").toDomain().zone)
        assertNull(dto.place.copy(tz = null).toDomain().zone)
        assertTrue(dto.copy(tripCancelled = true).toDomain().cancelled)
    }

    @Test fun stopTimeAlerts() {
        val departures = fixture<StoptimesResponse>("stoptimes-alerts").stopTimes.map { it.toDomain() }
        assertEquals(listOf(1, 0, 3, 1), departures.map { it.alerts.size })

        val bus = departures.first().alerts.single()
        assertEquals("Bus 57 : Travaux - Arrêt(s) non desservi(s)", bus.header)
        assertEquals(AlertEffect.MODIFIED_SERVICE, bus.effect)
        assertEquals(AlertCause.CONSTRUCTION, bus.cause)
        // The feed gives no severity and no link.
        assertEquals(AlertSeverity.UNKNOWN, bus.severity)
        assertNull(bus.url)
        assertEquals(
            listOf(DomainRange(Instant.parse("2026-08-12T15:36:00Z"), Instant.parse("2027-02-01T02:30:00Z"))),
            bus.impactPeriods,
        )

        // The fixture adds a severity and a link to this alert.
        val metro = departures.last().alerts.single()
        assertEquals(AlertSeverity.SEVERE, metro.severity)
        assertEquals(AlertEffect.NO_SERVICE, metro.effect)
        assertEquals("https://example.org/alerts/metro-6", metro.url)
    }

    @Test fun unknownAlertValuesFallBack() {
        val alert = Alert(
            headerText = "Header", descriptionText = "", url = " ", severityLevel = "CATASTROPHIC", effect = "OTHER_EFFECT",
            cause = "UNKNOWN_CAUSE",
            impactPeriod = listOf(TimeRange(start = "not a time"), TimeRange(end = "2026-11-09T10:00:00Z"), TimeRange()),
        ).toDomain()

        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
        assertNull(alert.effect)
        assertNull(alert.cause)
        assertNull(alert.url)
        assertEquals(
            listOf(DomainRange(null, Instant.parse("2026-11-09T10:00:00Z")), DomainRange(null, null)),
            alert.impactPeriods,
        )
    }

    @Test fun missionCodeHeadsignBecomesFinalStop() {
        val idfm = fixture<StoptimesResponse>("stoptimes-idfm").stopTimes
        val (westbound, eastbound) = idfm.take(2).map { it.toDomain() }
        assertEquals("Cergy le Haut", westbound.headsign)
        assertEquals("Marne-la-Vallée - Chessy", eastbound.headsign)
        assertEquals("QOKA52", eastbound.tripCode)
        assertEquals("fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:71517", eastbound.stop.stationId)
        // Berlin headsigns name places and stay as they are. The final stop has a city suffix.
        val berlin = fixture<StoptimesResponse>("stoptimes").stopTimes.first()
        assertEquals("U Turmstr.", berlin.toDomain().headsign)
        assertNull(berlin.toDomain().tripCode)
        assertEquals("U Turmstr. (Berlin)", berlin.copy(headsign = " ").toDomain().headsign)
        assertNull(berlin.copy(headsign = null, tripTo = null).toDomain().headsign)
        val leg = fixture<PlanResponse>("plan").itineraries.first().legs[1]
            .copy(headsign = "QOKA", tripShortName = "QOKA52", tripTo = idfm[1].tripTo).toDomain() as DomainLeg.Transit
        assertEquals("Marne-la-Vallée - Chessy", leg.headsign)
        assertEquals("QOKA52", leg.tripCode)
    }

    @Test fun colorsAndLineFallback() {
        assertEquals(0xFF00AAFF.toInt(), parseColor("00aAff"))
        listOf(null, "", "#00AAFF", "GG0000", "12345", "FFFFFFFF").forEach { assertNull(parseColor(it)) }
        val dto = fixture<StoptimesResponse>("stoptimes").stopTimes.first().copy(
            routeShortName = "", displayName = "Display", tripShortName = "Trip", routeLongName = "Long",
            routeColor = "123ABC", routeTextColor = "FFFFFF",
        )
        assertEquals("Display", dto.toDomain().line.name)
        assertEquals("Trip", dto.copy(displayName = "").toDomain().line.name)
        assertEquals("Long", dto.copy(displayName = null, tripShortName = "").toDomain().line.name)
        assertEquals("", dto.copy(displayName = null, tripShortName = null, routeLongName = null).toDomain().line.name)
        assertEquals(0xFF123ABC.toInt(), dto.toDomain().line.color)
        assertEquals(0xFFFFFFFF.toInt(), dto.toDomain().line.textColor)
    }

    @Test fun modeAliasesAndStreetDistance() {
        val aliases = mapOf(
            "CAR_PARKING" to TransitMode.CAR, "CAR_DROPOFF" to TransitMode.CAR,
            "METRO" to TransitMode.SUBWAY, "REGIONAL_FAST_RAIL" to TransitMode.RAIL,
            "NIGHT_RAIL" to TransitMode.RAIL, "LONG_DISTANCE" to TransitMode.RAIL,
            "AREAL_LIFT" to TransitMode.AERIAL_LIFT, "CABLE_CAR" to TransitMode.AERIAL_LIFT,
            "ODM" to TransitMode.ON_DEMAND, "FLEX" to TransitMode.ON_DEMAND,
            "RIDE_SHARING" to TransitMode.ON_DEMAND, "RENTAL" to TransitMode.BIKE,
            "FUTURE_MODE" to TransitMode.OTHER,
        )
        aliases.forEach { (input, expected) -> assertEquals(expected, transitMode(input)) }
        val leg = fixture<PlanResponse>("plan").itineraries.first().legs.first()
        assertEquals(0.0, (leg.copy(distance = null).toDomain() as DomainLeg.Street).distanceMeters, 0.0)
        assertEquals(TransitMode.OTHER, leg.copy(mode = "FUTURE_MODE").toDomain().mode)
    }

    @Test fun walkStepsMapWithStairsAndBlankNames() {
        val walk = fixture<PlanResponse>("plan").toDomain().journeys.first().legs.first() as DomainLeg.Street
        assertEquals(32, walk.steps.size)
        // The fixture gives no street names and only "continue" and "stairs".
        assertTrue(walk.steps.all { it.streetName == null && it.turn == null })
        assertEquals(1, walk.steps.count { it.kind == app.itivio.domain.StepKind.STAIRS })
        assertTrue(walk.steps.all { it.geometry.size >= 2 })
        val route = app.itivio.domain.WalkRoute.of(walk.steps, walk.geometry)
        assertEquals(walk.distanceMeters, route.lengthMeters, walk.distanceMeters * 0.05)
        val maneuvers = route.instructions.map { it.maneuver }
        assertEquals(app.itivio.domain.Maneuver.DEPART, maneuvers.first())
        assertEquals(app.itivio.domain.Maneuver.ARRIVE, maneuvers.last())
        assertTrue(app.itivio.domain.Maneuver.STAIRS in maneuvers)
        assertTrue(maneuvers.any { it == app.itivio.domain.Maneuver.LEFT || it == app.itivio.domain.Maneuver.RIGHT })
    }

    @Test fun directJourneysComeAfterTransitWithStableIds() {
        val page = fixture<PlanResponse>("plan-direct").toDomain("Hôtel de Ville", "Louvre")
        val walk = page.journeys.single()
        assertTrue(walk.isStreetOnly)
        // MOTIS gives direct journeys a blank ID; the mapper derives one.
        assertTrue(walk.id.isNotBlank())
        assertEquals(walk.id, fixture<PlanResponse>("plan-direct").toDomain().journeys.single().id)
        assertEquals("Louvre", walk.legs.last().to.name)
        assertTrue((walk.legs.single() as DomainLeg.Street).steps.isNotEmpty())
    }

    @Test fun tripFixtureGivesAllStopsWithLiveTimes() {
        val trip = fixture<Itinerary>("trip").toTrip()
        assertEquals(13, trip.stops.size)
        assertEquals("S+U Berlin Hauptbahnhof", trip.stops.first().name)
        assertEquals("Jüterbog, Bahnhof", trip.stops.last().name)
        assertTrue(trip.stops.first().departure!!.isRealtime)
        // Woltersdorf runs a minute early.
        val early = trip.stops.first { it.name.startsWith("Woltersdorf") }
        assertEquals(-60, early.arrival!!.delay!!.seconds)
        assertFalse(trip.cancelled)
    }
}
