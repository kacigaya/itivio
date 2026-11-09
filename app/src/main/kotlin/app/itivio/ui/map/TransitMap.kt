package app.itivio.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.TransitMode
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.Material3AttributionOnly
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.JsonObject

// OpenFreeMap: free OpenStreetMap vector tiles, no API key, no tracking.
private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** Europe-wide view until the first location fix or route arrives. */
private val InitialCamera = CameraPosition(center = Position(longitude = 10.0, latitude = 50.0), zoom = 3.5)

/**
 * The persistent map behind every screen. Draws the device position, nearby [stops], the
 * [journey] legs, and the [destination] marker. [insets] is the area covered by sheets or panes; the camera keeps
 * routes clear of it. Each change of [recenterRequest] moves the camera to the device position.
 */
@Composable
fun TransitMap(
    locationState: LocationState,
    journey: Journey?,
    destination: GeoPoint?,
    stops: List<GeoPoint>,
    insets: PaddingValues,
    recenterRequest: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = if (isSystemInDarkTheme()) DARK_STYLE else LIGHT_STYLE
    val mapState = rememberMapState(baseStyle = BaseStyle.Uri(style), initialCameraPosition = InitialCamera) {
        // Layers declared here render above the base style and follow recomposition.
        StopsLayer(stops, fill = scheme.surface, stroke = scheme.primary)
        LocationIndicatorLayer(id = "user", locationState = locationState)
        if (journey != null) JourneyLayers(journey, accent = scheme.primary, casing = scheme.surface, walk = scheme.onSurfaceVariant)
        EndpointLayer(journey, destination, fill = scheme.primary, stroke = scheme.surface)
    }
    MaplibreMap(
        modifier = modifier,
        state = mapState,
        viewportInsets = insets,
        overlay = { include(MapOverlay.Material3AttributionOnly) },
    )

    LaunchedEffect(journey?.id) {
        val box = journey?.bounds() ?: return@LaunchedEffect
        mapState.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)))
    }
    LaunchedEffect(recenterRequest) {
        if (recenterRequest == 0) return@LaunchedEffect
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        mapState.animateCamera(CameraUpdate(center = here, zoom = maxOf(mapState.cameraPosition.zoom, 15.0)))
    }
    // First fix: move from the overview to the user once, unless a route is already shown.
    val firstFix = locationState.lastLocation != null
    LaunchedEffect(firstFix) {
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        if (journey == null) mapState.animateCamera(CameraUpdate(center = here, zoom = 15.0))
    }
}

@Composable
@MaplibreComposable
private fun JourneyLayers(journey: Journey, accent: Color, casing: Color, walk: Color) {
    journey.legs.forEachIndexed { index, leg ->
        if (leg.geometry.size < 2) return@forEachIndexed
        key(journey.id, index) {
            val source = rememberGeoJsonSource(GeoJsonData.Features(leg.geometry.toLineString()))
            if (leg is Leg.Transit) {
                LineLayer(
                    id = "leg-$index-casing",
                    source = source,
                    color = const(casing),
                    width = const(9.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = "leg-$index",
                    source = source,
                    color = const(leg.line.color?.let(::Color) ?: accent),
                    width = const(5.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            } else {
                LineLayer(
                    id = "leg-$index",
                    source = source,
                    color = const(if (leg.mode == TransitMode.WALK) walk else accent),
                    width = const(4.dp),
                    dasharray = const(listOf(0.5, 2)),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
        }
    }
}

@Composable
@MaplibreComposable
private fun StopsLayer(stops: List<GeoPoint>, fill: Color, stroke: Color) {
    val source = rememberGeoJsonSource(GeoJsonData.Features(remember(stops) { stops.toPoints() }))
    CircleLayer(
        id = "stops",
        source = source,
        color = const(fill),
        radius = const(5.dp),
        strokeColor = const(stroke),
        strokeWidth = const(2.dp),
    )
}

@Composable
@MaplibreComposable
private fun EndpointLayer(journey: Journey?, destination: GeoPoint?, fill: Color, stroke: Color) {
    val endpoints = remember(journey?.id, destination) {
        buildList {
            journey?.legs?.firstOrNull()?.from?.point?.let(::add)
            (journey?.legs?.lastOrNull()?.to?.point ?: destination)?.let(::add)
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(endpoints.toPoints()))
    CircleLayer(
        id = "endpoints",
        source = source,
        color = const(fill),
        radius = const(7.dp),
        strokeColor = const(stroke),
        strokeWidth = const(3.dp),
    )
}

private fun List<GeoPoint>.toLineString() =
    LineString(map { Position(longitude = it.longitude, latitude = it.latitude) })

private fun List<GeoPoint>.toPoints() = FeatureCollection(
    map { Feature<Point, JsonObject?>(Point(Position(longitude = it.longitude, latitude = it.latitude)), null) },
)

private fun Journey.bounds(): BoundingBox? {
    val points = legs.flatMap { leg -> leg.geometry.ifEmpty { listOf(leg.from.point, leg.to.point) } }
    if (points.isEmpty()) return null
    return BoundingBox(
        west = points.minOf { it.longitude },
        south = points.minOf { it.latitude },
        east = points.maxOf { it.longitude },
        north = points.maxOf { it.latitude },
    )
}
