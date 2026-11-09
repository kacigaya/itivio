package app.itivio.data

import app.itivio.data.transitous.*
import app.itivio.domain.Leg as DomainLeg
import app.itivio.domain.TransitMode
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class TransitousMapperTest {
    private inline fun <reified T> fixture(name: String): T = transitousJson.decodeFromString(
        requireNotNull(javaClass.getResource("/transitous/$name.json")).readText(),
    )

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
}
