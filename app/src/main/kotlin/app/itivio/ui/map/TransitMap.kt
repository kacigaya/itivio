package app.itivio.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkRoute
import app.itivio.R
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.location.mostAccurateBearing
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import kotlin.math.roundToInt
import app.itivio.ui.common.icon
import app.itivio.ui.common.lineColors
import app.itivio.ui.theme.LocalDarkTheme
import java.util.Locale
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.itivio.domain.BoundingBox as StopBox
import kotlinx.coroutines.flow.collectLatest
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
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorDefaults
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.MapStyleState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.AttributionButtonDefaults
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MapOverlay
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

/**
 * A transit stop on the map. A tap on it opens its departures. [line] and [mode] are those of the
 * next departure, when the app knows it; the stop then shows as a pin in the line color.
 */
data class MapStop(
    val stopId: String,
    val name: String,
    val point: GeoPoint,
    val line: Line? = null,
    val mode: TransitMode? = null,
)

/** Stops load only at this zoom and closer, where a screen shows a few streets. */
const val MAP_STOPS_MIN_ZOOM = 15.0

/** Walking view: the camera looks ahead, over a few streets, with the map tilted. */
private const val NAVIGATION_ZOOM = 18.0
private const val NAVIGATION_PITCH = 50.0

/** Walking view: the share of the free map height that sits above the position, under the header. */
private const val NAVIGATION_LOOK_AHEAD = 0.3f

/**
 * The persistent map behind every screen. Draws the device position, nearby [stops], the stops
 * of the area in view ([areaStops]), the [journey] legs, and the [destination] marker. [insets] is
 * the area covered by sheets or panes; the camera keeps routes clear of it. Each change of
 * [recenterRequest] moves the camera to the device position. A change of [focus] moves the camera
 * to that point. While [follow] is set, the camera follows the device position and heading; a
 * gesture on the map stops it and calls [onFollowStopped]. [onCameraIdle] gets the area in view
 * each time the camera stops.
 *
 * With [walk], the map is the walking view: the walk replaces the legs of [journey], the part
 * walked is muted, and the position is an arrow. While the camera follows, the map is tilted and
 * turns with the walker, unless [northUp] is set. The position then sits low on the screen, so the
 * walker sees the way ahead. Each change of [overviewRequest] shows the whole journey. [onBearing]
 * gets the bearing of the camera, for the compass.
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
    walk: WalkRoute? = null,
    northUp: Boolean = false,
    overviewRequest: Int = 0,
    onBearing: (Double) -> Unit = {},
    onStop: (MapStop) -> Unit = {},
    onFollowStopped: () -> Unit = {},
    onCameraIdle: (StopBox, Double) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    val style = if (LocalDarkTheme.current) DARK_STYLE else LIGHT_STYLE
    val currentOnStop by rememberUpdatedState(onStop)
    val pins = stops.map { stop ->
        val (container, content) = stop.line?.let { lineColors(it) } ?: (scheme.secondaryContainer to scheme.onSecondaryContainer)
        StopPin(stop, container, content)
    }
    // The position on the walk, in metres along it. Rounded, so the route redraws only once the walker has moved.
    val walkedMeters by remember(walk, locationState) {
        derivedStateOf {
            val here = locationState.lastLocation?.position ?: return@derivedStateOf 0
            val progress = walk?.progress(GeoPoint(here.latitude, here.longitude)) ?: return@derivedStateOf 0
            if (progress.isOffRoute) 0 else progress.alongMeters.toInt()
        }
    }
    // The direction of the arrow in the walking view, in whole degrees: the heading of the device,
    // else the route ahead. Without a compass, as on many tablets, the arrow still points the way.
    val puckRotation by remember(walk, locationState) {
        derivedStateOf {
            val heading = locationState.mostAccurateBearing()?.let { (it - Bearing.North).inDegrees }
            (heading ?: walk?.directionAt(walkedMeters.toDouble()) ?: 0.0).roundToInt()
        }
    }
    // The arrow and the camera of the walking view use the nearest point of the route when it is
    // near, so position noise does not move them off the line.
    val puckPosition by remember(walk, locationState) {
        derivedStateOf {
            val here = locationState.lastLocation?.position ?: return@derivedStateOf null
            val route = walk ?: return@derivedStateOf here
            val point = route.snap(GeoPoint(here.latitude, here.longitude))
            Position(longitude = point.longitude, latitude = point.latitude)
        }
    }
    val puck = navigationPuck(fill = scheme.primary, border = scheme.surface, arrow = scheme.onPrimary)
    val mapState = rememberMapState(baseStyle = BaseStyle.Uri(style), initialCameraPosition = InitialCamera) {
        // Layers declared here render above the base style and follow recomposition.
        StopsLayer("area-stops", areaStops, fill = scheme.surface, stroke = scheme.outline, minZoom = MAP_STOPS_MIN_ZOOM - 0.5) { currentOnStop(it) }
        StopPinsLayer("stops", pins, casing = scheme.surface) { currentOnStop(it) }
        LocationIndicatorLayer(
            id = "user",
            locationState = locationState,
            accuracyRadiusColor = scheme.primary.copy(alpha = 0.2f),
            accuracyRadiusBorderColor = scheme.primary.copy(alpha = 0.4f),
            // The border uses the surface, as the stop pins do. A white border merges with a light primary in dark schemes.
            topImage = LocationIndicatorDefaults.topImage(color = scheme.primary, borderColor = scheme.surface),
            bearingImage = LocationIndicatorDefaults.bearingImage(color = scheme.primary),
            // The walking view draws its own arrow, after the walk.
            visible = walk == null,
        )
        if (walk != null) {
            WalkLayers(walk, walkedMeters.toDouble(), ahead = scheme.primary, walked = scheme.outline, casing = scheme.surface)
        } else if (journey != null) {
            // A walk drawn alone, as in the walking view, is the route to follow: it gets the accent.
            val walk = if (journey.isStreetOnly) scheme.primary else scheme.onSurfaceVariant
            JourneyLayers(journey, accent = scheme.primary, casing = scheme.surface, walk = walk)
        }
        EndpointLayer(journey, destination, fill = scheme.primary, stroke = scheme.surface)
        if (walk != null) PuckLayer(puckPosition, puckRotation, puck)
    }
    // While the camera follows the walker, a larger top inset puts the position low on the screen.
    var mapHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val viewport = if (walk != null && follow) {
        val top = insets.calculateTopPadding()
        val free = (mapHeight - top - insets.calculateBottomPadding()).coerceAtLeast(0.dp)
        PaddingValues(
            start = insets.calculateStartPadding(direction),
            top = top + free * NAVIGATION_LOOK_AHEAD,
            end = insets.calculateEndPadding(direction),
            bottom = insets.calculateBottomPadding(),
        )
    } else {
        insets
    }
    MaplibreMap(
        modifier = modifier.onSizeChanged { mapHeight = with(density) { it.height.toDp() } },
        state = mapState,
        viewportInsets = viewport,
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
    LaunchedEffect(overviewRequest) {
        if (overviewRequest == 0) return@LaunchedEffect
        val box = journey?.bounds() ?: return@LaunchedEffect
        mapState.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)))
    }
    // Out of the walking view with no route to fit, the map stands flat with north up again.
    LaunchedEffect(walk == null) {
        if (walk != null || journey != null) return@LaunchedEffect
        val camera = mapState.cameraPosition
        if (camera.pitch != 0.0 || camera.bearing != 0.0) mapState.animateCamera(CameraUpdate(bearing = 0.0, pitch = 0.0))
    }
    // Keyed on the first fix too: after process death, the view opens before the position is known.
    val hasFix = locationState.lastLocation != null
    val navigation = walk != null
    LaunchedEffect(follow, hasFix, navigation) {
        if (!follow || navigation) return@LaunchedEffect
        val here = locationState.lastLocation?.position ?: return@LaunchedEffect
        mapState.animateCamera(CameraUpdate(center = here, zoom = maxOf(mapState.cameraPosition.zoom, 17.0)))
    }
    LocationTrackingEffect(locationState, enabled = follow && !navigation, trackBearing = true) {
        updateCamera(mapState, updateBearing = BearingUpdate.TrackAutomatic)
    }
    // The walking view moves the camera with the position and the arrow. One update sets all of
    // the camera: a second animation at the same time would cancel the first.
    LaunchedEffect(follow, navigation, northUp) {
        if (!follow || !navigation) return@LaunchedEffect
        snapshotFlow { puckPosition to puckRotation }
            .distinctUntilChanged()
            .collectLatest { (here, rotation) ->
                if (here == null) return@collectLatest
                val update = if (northUp) {
                    CameraUpdate(center = here, zoom = NAVIGATION_ZOOM, bearing = 0.0, pitch = 0.0)
                } else {
                    CameraUpdate(center = here, zoom = NAVIGATION_ZOOM, bearing = rotation.toDouble(), pitch = NAVIGATION_PITCH)
                }
                mapState.animateCamera(update)
            }
    }
    val currentOnBearing by rememberUpdatedState(onBearing)
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.cameraPosition.bearing }.distinctUntilChanged().collect { currentOnBearing(it) }
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
 * The map credits. They stay behind the info button until the user opens them. The library
 * button starts open on each launch and covers the map above the sheet.
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

/**
 * The walk of the walking view, dotted: the part ahead in [ahead], the part walked in [walked].
 * [walkedMeters] is the distance walked along it.
 */
@Composable
@MaplibreComposable
private fun WalkLayers(walk: WalkRoute, walkedMeters: Double, ahead: Color, walked: Color, casing: Color) {
    val (done, rest) = remember(walk, walkedMeters) { walk.split(walkedMeters) }
    val doneSource = rememberGeoJsonSource(GeoJsonData.Features(done.toLineString()))
    val restSource = rememberGeoJsonSource(GeoJsonData.Features(rest.toLineString()))
    LineLayer(
        id = "walk-done",
        source = doneSource,
        color = const(walked),
        width = const(5.dp),
        dasharray = const(listOf(0.0, 2.4)),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    // A casing under the dots keeps them clear on busy streets, as the transit lines have.
    LineLayer(
        id = "walk-ahead-casing",
        source = restSource,
        color = const(casing),
        width = const(9.dp),
        // Zero-length dashes with round caps are dots. The period is the same as the dots (12dp),
        // so each dot has its ring.
        dasharray = const(listOf(0.0, 12.0 / 9)),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    LineLayer(
        id = "walk-ahead",
        source = restSource,
        color = const(ahead),
        width = const(6.dp),
        dasharray = const(listOf(0.0, 2.0)),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
}

/**
 * The position in the walking view, as an arrow that lies flat on the map and turns to [rotation]
 * degrees from north. Nothing shows before the first fix.
 */
@Composable
@MaplibreComposable
private fun PuckLayer(position: Position?, rotation: Int, puck: Expression<ImageValue>) {
    val points = remember(position) {
        FeatureCollection(listOfNotNull(position).map { Feature<Point, JsonObject?>(Point(it), null) })
    }
    SymbolLayer(
        id = "walk-puck",
        source = rememberGeoJsonSource(GeoJsonData.Features(points)),
        iconImage = puck,
        iconRotate = const(rotation.toFloat()),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconPitchAlignment = const(IconPitchAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** The position in the walking view: an arrow on a disc, with a border that keeps it clear of the route. */
@Composable
private fun navigationPuck(fill: Color, border: Color, arrow: Color): Expression<ImageValue> {
    val icon = painterResource(R.drawable.ic_navigation)
    val painter = remember(icon, fill, border, arrow) { PuckPainter(icon, fill, border, arrow) }
    return image(painter, size = DpSize(PuckSize, PuckSize))
}

private val PuckSize = 40.dp

private class PuckPainter(
    private val icon: Painter,
    private val fill: Color,
    private val border: Color,
    private val arrow: Color,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val radius = size.minDimension / 2
        drawCircle(border, radius)
        drawCircle(fill, radius * 0.85f)
        val inner = size * 0.6f
        translate(left = (size.width - inner.width) / 2, top = (size.height - inner.height) / 2) {
            with(icon) { draw(inner, colorFilter = ColorFilter.tint(arrow)) }
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

/** A [MapStop] with the colors of its pin. */
private data class StopPin(val stop: MapStop, val container: Color, val content: Color)

/**
 * Stops as pins: a disc in the color of the line, with the icon of the mode. The icons are SDF
 * images, so each pin can tint its icon with the text color of its line.
 */
@Composable
@MaplibreComposable
private fun StopPinsLayer(id: String, pins: List<StopPin>, casing: Color, onStop: (MapStop) -> Unit) {
    val features = remember(pins) {
        FeatureCollection(
            pins.map { pin ->
                Feature(
                    Point(Position(longitude = pin.stop.point.longitude, latitude = pin.stop.point.latitude)),
                    buildJsonObject {
                        put("id", pin.stop.stopId)
                        put("color", pin.container.toCss())
                        put("content", pin.content.toCss())
                        put("mode", (pin.stop.mode ?: TransitMode.OTHER).name)
                    },
                )
            },
        )
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(features))
    CircleLayer(
        id = id,
        source = source,
        color = feature.get("color").convertToColor(),
        radius = const(11.dp),
        strokeColor = const(casing),
        strokeWidth = const(2.dp),
        // The disc is 26dp with its casing; the padding brings the target to about 48dp.
        hitPadding = 11.dp,
        onClick = { features ->
            val stopId = features.firstNotNullOfOrNull { it.properties?.get("id")?.jsonPrimitive?.contentOrNull }
            val stop = pins.firstOrNull { it.stop.stopId == stopId }?.stop
            if (stop == null) ClickResult.Pass else ClickResult.Consume.also { onStop(stop) }
        },
    )
    val icons = TransitMode.entries.filter { it.isTransit }.map { mode -> case(mode.name, pinIcon(mode)) }
    SymbolLayer(
        id = "$id-icons",
        source = source,
        iconImage = switch(feature.get("mode"), *icons.toTypedArray(), fallback = pinIcon(TransitMode.OTHER)),
        iconColor = feature.get("content").convertToColor(),
        // A pin always shows its icon, also when it sits on a label of the base map.
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

@Composable
private fun pinIcon(mode: TransitMode) = image(painterResource(mode.icon()), size = DpSize(14.dp, 14.dp), drawAsSdf = true)

private fun Color.toCss(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

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
