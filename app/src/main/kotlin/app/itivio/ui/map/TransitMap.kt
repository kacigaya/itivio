package app.itivio.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.TransitMode
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.itivio.domain.BoundingBox as StopBox
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.updateCamera
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.MapStyleState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.AttributionButtonDefaults
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.overlay.attributions
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
// The Liberty layers with dark colors, built by tools/dark-map-style.py. The OpenFreeMap dark
// style has too little contrast and no stop or place labels.
private const val DARK_STYLE = "asset://map/dark.json"

/** Europe-wide view until the first location fix or route arrives. */
private val InitialCamera = CameraPosition(center = Position(longitude = 10.0, latitude = 50.0), zoom = 3.5)

/** A transit stop on the map. A tap on it opens its departures. */
data class MapStop(val stopId: String, val name: String, val point: GeoPoint)

/** Stops load only at this zoom and closer, where a screen shows a few streets. */
const val MAP_STOPS_MIN_ZOOM = 15.0

/**
 * The persistent map behind every screen. Draws the device position, nearby [stops], the stops
 * of the area in view ([areaStops]), the [journey] legs, and the [destination] marker. [insets] is
 * the area covered by sheets or panes; the camera keeps routes clear of it. Each change of
 * [recenterRequest] moves the camera to the device position. A change of [focus] moves the camera
 * to that point. While [follow] is set, the camera follows the device position and heading; a
 * gesture on the map stops it and calls [onFollowStopped]. [onCameraIdle] gets the area in view
 * each time the camera stops.
 */
@Composable
fun TransitMap(
    locationState: LocationState,
    journey: Journey?,
    destination: GeoPoint?,
    stops: List<MapStop>,
    insets: PaddingValues,
    recenterRequest: Int,
    modifier: Modifier = Modifier,
    areaStops: List<MapStop> = emptyList(),
    focus: GeoPoint? = null,
    follow: Boolean = false,
    onStop: (MapStop) -> Unit = {},
    onFollowStopped: () -> Unit = {},
    onCameraIdle: (StopBox, Double) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    val style = if (isSystemInDarkTheme()) DARK_STYLE else LIGHT_STYLE
    val currentOnStop by rememberUpdatedState(onStop)
    val mapState = rememberMapState(baseStyle = BaseStyle.Uri(style), initialCameraPosition = InitialCamera) {
        // Layers declared here render above the base style and follow recomposition.
        StopsLayer("area-stops", areaStops, fill = scheme.surface, stroke = scheme.outline, minZoom = MAP_STOPS_MIN_ZOOM - 0.5) { currentOnStop(it) }
        StopsLayer("stops", stops, fill = scheme.surface, stroke = scheme.primary) { currentOnStop(it) }
        LocationIndicatorLayer(id = "user", locationState = locationState)
        if (journey != null) JourneyLayers(journey, accent = scheme.primary, casing = scheme.surface, walk = scheme.onSurfaceVariant)
        EndpointLayer(journey, destination, fill = scheme.primary, stroke = scheme.surface)
    }
    MaplibreMap(
        modifier = modifier,
        state = mapState,
        viewportInsets = insets,
        overlay = { MapCredits(mapState.style) },
    )

    LaunchedEffect(journey?.id) {
        // While the camera follows the walker, a fit to the route would fight it.
        if (follow) return@LaunchedEffect
        val box = journey?.bounds() ?: return@LaunchedEffect
        mapState.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)))
    }
    LaunchedEffect(recenterRequest) {
        if (recenterRequest == 0) return@LaunchedEffect
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        mapState.animateCamera(CameraUpdate(center = here, zoom = maxOf(mapState.cameraPosition.zoom, 15.0)))
    }
    LaunchedEffect(focus) {
        val point = focus ?: return@LaunchedEffect
        mapState.animateCamera(
            CameraUpdate(center = Position(longitude = point.longitude, latitude = point.latitude), zoom = maxOf(mapState.cameraPosition.zoom, 16.0)),
        )
    }
    // Keyed on the first fix too: after process death, the view opens before the position is known.
    val hasFix = locationState.lastLocation != null
    LaunchedEffect(follow, hasFix) {
        if (!follow) return@LaunchedEffect
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        mapState.animateCamera(CameraUpdate(center = here, zoom = maxOf(mapState.cameraPosition.zoom, 17.0)))
    }
    LocationTrackingEffect(locationState, enabled = follow, trackBearing = true) {
        updateCamera(mapState, updateBearing = BearingUpdate.TrackAutomatic)
    }
    val currentOnFollowStopped by rememberUpdatedState(onFollowStopped)
    LaunchedEffect(mapState, follow) {
        if (!follow) return@LaunchedEffect
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.Gesture }
            .filter { it }
            .collect { currentOnFollowStopped() }
    }
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    LaunchedEffect(mapState) {
        snapshotFlow { if (mapState.isCameraMoving) null else mapState.cameraPosition }
            .distinctUntilChanged()
            .collect { position ->
                if (position == null) return@collect
                // The map has no area before its first layout.
                val bounds = runCatching { mapState.getVisibleBounds() }.getOrNull() ?: return@collect
                currentOnCameraIdle(StopBox(bounds.south, bounds.west, bounds.north, bounds.east), position.zoom)
            }
    }
    // First fix: move from the overview to the user once, unless a route is already shown or the
    // camera follows the walker.
    LaunchedEffect(hasFix) {
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        if (journey == null && !follow) mapState.animateCamera(CameraUpdate(center = here, zoom = 15.0))
    }
}

/**
 * The MapLibre logo and the map credits. The credits stay behind the info button until the user
 * opens them. The library button starts open on each launch and covers the map above the sheet.
 */
@Composable
private fun MapCredits(style: MapStyleState) {
    val credits by remember(style) { derivedStateOf { style.attributions() } }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val insets = LocalViewportInsets.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(insets)
            .consumeWindowInsets(insets)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MapOverlay.Spacing),
    ) {
        MaplibreLogo(Modifier.align(Alignment.BottomStart))
        if (credits.isEmpty()) return@Box
        Surface(
            color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    AttributionLinks(
                        credits,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = LocalContentColor.current),
                        modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
                    )
                }
                AttributionButtonDefaults.button { expanded = !expanded }
            }
        }
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
private fun StopsLayer(
    id: String,
    stops: List<MapStop>,
    fill: Color,
    stroke: Color,
    minZoom: Double = 0.0,
    onStop: (MapStop) -> Unit,
) {
    val source = rememberGeoJsonSource(GeoJsonData.Features(remember(stops) { stops.toFeatures() }))
    CircleLayer(
        id = id,
        source = source,
        minZoom = minZoom.toFloat(),
        color = const(fill),
        radius = const(5.dp),
        strokeColor = const(stroke),
        strokeWidth = const(2.dp),
        // The rings are small; the padding gives the finger a larger target.
        hitPadding = 12.dp,
        onClick = { features ->
            val stopId = features.firstNotNullOfOrNull { it.properties?.get("id")?.jsonPrimitive?.contentOrNull }
            val stop = stops.firstOrNull { it.stopId == stopId }
            if (stop == null) ClickResult.Pass else ClickResult.Consume.also { onStop(stop) }
        },
    )
}

private fun List<MapStop>.toFeatures() = FeatureCollection(
    map {
        Feature(
            Point(Position(longitude = it.point.longitude, latitude = it.point.latitude)),
            buildJsonObject { put("id", it.stopId) },
        )
    },
)

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
