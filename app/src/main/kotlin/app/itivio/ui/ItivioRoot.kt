package app.itivio.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.Stop
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.LocalPanelHeaderHeight
import app.itivio.ui.common.isWideLayout
import app.itivio.ui.common.panelInsets
import app.itivio.ui.explore.ExplorePeekHeight
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.itinerary.ItineraryPeekHeight
import app.itivio.ui.itinerary.ItineraryScreen
import app.itivio.ui.itinerary.LineTimesViewModel
import app.itivio.ui.itinerary.TripTrackerViewModel
import app.itivio.ui.map.MapStop
import app.itivio.ui.map.MapStopsViewModel
import app.itivio.ui.map.TransitMap
import app.itivio.ui.nearby.NearbyViewModel
import app.itivio.ui.nearby.rememberNow
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.results.ResultsPeekHeight
import app.itivio.ui.results.ResultsScreen
import app.itivio.ui.stop.StopDeparturesViewModel
import app.itivio.ui.stop.StopPeekHeight
import app.itivio.ui.stop.StopScreen
import app.itivio.ui.walk.WalkPeekHeight
import app.itivio.ui.walk.WalkScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.HeadingMeasurement
import org.maplibre.compose.location.HeadingProvider
import org.maplibre.compose.location.HeadingRequest
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState

@Serializable data object ExploreRoute : NavKey
@Serializable data class StopRoute(val stopId: String, val name: String, val latitude: Double, val longitude: Double) : NavKey
@Serializable data object ResultsRoute : NavKey
@Serializable data class ItineraryRoute(val journeyId: String) : NavKey
@Serializable data class WalkRoute(val journeyId: String, val legIndex: Int) : NavKey

/** Routes that show the planning session. After process death they wait for the session to plan again. */
private val NavKey.needsSession: Boolean get() = this == ResultsRoute || this is ItineraryRoute || this is WalkRoute

/**
 * One map stays alive behind all screens; each screen draws its sheet or pane over it. The
 * planner ViewModel is scoped to the activity so every screen reads the same session.
 */
@Composable
fun ItivioRoot(
    planner: PlannerViewModel = koinViewModel(),
    nearby: NearbyViewModel = koinViewModel(),
    mapStops: MapStopsViewModel = koinViewModel(),
) {
    val backStack = rememberNavBackStack(ExploreRoute)
    val state by planner.state.collectAsStateWithLifecycle()
    // Navigation survives process death. The planner restores its request and plans again; without
    // a request, the screens of the session close.
    LaunchedEffect(Unit) {
        if (state.destination == null) {
            val first = backStack.indexOfFirst { it.needsSession }
            if (first >= 0) while (backStack.size > first) backStack.removeAt(backStack.lastIndex)
        }
    }
    val wide = isWideLayout()
    val userLocation = koinInject<UserLocation>()
    val top = backStack.last()

    // The compass runs only in the walking view.
    val heading = rememberDefaultHeadingProvider()
    val locationState = rememberLocationState(
        provider = rememberDefaultLocationProvider(),
        headingProvider = if (top is WalkRoute) heading else NoHeading,
    )
    val locationGranted = locationState.permission is LocationPermission.Granted
    LaunchedEffect(locationState) {
        snapshotFlow { locationState.lastLocation?.position }.collect { position ->
            userLocation.point.value = position?.let { GeoPoint(it.latitude, it.longitude) }
        }
    }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    var following by rememberSaveable { mutableStateOf(true) }
    // Collected only while Explore is on top, so departures refresh only while they are visible.
    val nearbyState = if (top == ExploreRoute && locationGranted) {
        nearby.departures.collectAsStateWithLifecycle().value
    } else {
        LoadState.Idle
    }
    val areaStops = mapStops.stops.collectAsStateWithLifecycle().value.takeIf { top == ExploreRoute }.orEmpty()
    val now by rememberNow()

    val walkLeg = (top as? WalkRoute)?.let { route -> state.journey(route.journeyId)?.legs?.getOrNull(route.legIndex) as? Leg.Street }
    val mapJourney = when (top) {
        is ItineraryRoute -> state.journey(top.journeyId)
        ResultsRoute -> state.highlighted
        // The walking view draws only its walk, from its start to its end.
        is WalkRoute -> walkLeg?.let { Journey("${top.journeyId}-walk-${top.legIndex}", listOf(it), it.start, it.end, 0) }
        else -> null
    }
    val peek = when (top) {
        ResultsRoute -> ResultsPeekHeight
        is ItineraryRoute -> ItineraryPeekHeight
        is StopRoute -> StopPeekHeight
        is WalkRoute -> WalkPeekHeight
        else -> ExplorePeekHeight
    }
    val stopPoint = (top as? StopRoute)?.let { GeoPoint(it.latitude, it.longitude) }
    val destinationPoint = stopPoint ?: (state.destination as? Endpoint.Place)?.location?.point?.takeIf { top.needsSession }
    val stops = (nearbyState as? LoadState.Ready)?.value.orEmpty()
        .flatMap { group -> group.departures.mapNotNull { departure -> departure.stop.toMapStop() } }
        .distinctBy { it.stopId }

    fun openStop(stop: MapStop) {
        backStack.add(StopRoute(stop.stopId, stop.name, stop.point.latitude, stop.point.longitude))
    }

    // The screen on top measures its floating header, so the camera keeps routes below it.
    val headerHeight = remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize()) {
        TransitMap(
            locationState = locationState,
            journey = mapJourney,
            destination = destinationPoint,
            stops = stops,
            insets = panelInsets(wide, peek, headerHeight.value),
            recenterRequest = recenterRequest,
            areaStops = areaStops,
            focus = stopPoint,
            follow = top is WalkRoute && following,
            onStop = ::openStop,
            onFollowStopped = { following = false },
            onCameraIdle = { box, zoom -> if (backStack.last() == ExploreRoute) mapStops.onCameraIdle(box, zoom) },
            modifier = Modifier.fillMaxSize(),
        )
        CompositionLocalProvider(LocalPanelHeaderHeight provides headerHeight) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.pop(planner) },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
                entryProvider = entryProvider {
                    entry<ExploreRoute> {
                        ExploreScreen(
                            wide = wide,
                            locationGranted = locationGranted,
                            nearby = nearbyState,
                            now = now,
                            onRequestLocation = locationState::requestPermission,
                            onRecenter = { recenterRequest++ },
                            onRetryNearby = nearby::retry,
                            onDestination = { endpoint ->
                                planner.setDestination(endpoint)
                                backStack.add(ResultsRoute)
                            },
                            onStop = { location ->
                                location.stopId?.let { openStop(MapStop(it, location.name, location.point)) }
                            },
                        )
                    }
                    entry<StopRoute> { route ->
                        // Scoped to this entry, so the board refreshes only while it is open.
                        val board: StopDeparturesViewModel = koinViewModel()
                        LaunchedEffect(route.stopId) { board.show(route.stopId) }
                        StopScreen(
                            wide = wide,
                            name = route.name,
                            departures = board.departures.collectAsStateWithLifecycle().value,
                            now = now,
                            onBack = { backStack.pop(planner) },
                            onDirections = {
                                val point = GeoPoint(route.latitude, route.longitude)
                                planner.setDestination(Endpoint.Place(Location(route.stopId, route.name, LocationKind.STOP, point, stopId = route.stopId)))
                                backStack.add(ResultsRoute)
                            },
                            onRetry = board::retry,
                        )
                    }
                    entry<ResultsRoute> {
                        ResultsScreen(
                            wide = wide,
                            state = state,
                            onBack = { backStack.pop(planner) },
                            onOrigin = { endpoint ->
                                if (endpoint == Endpoint.MyLocation && !locationGranted) locationState.requestPermission()
                                planner.setOrigin(endpoint)
                            },
                            onDestination = planner::setDestination,
                            onSwap = planner::swap,
                            onTime = planner::setTime,
                            onOptions = planner::setOptions,
                            onSort = planner::setSort,
                            onSelect = planner::select,
                            onOpen = { id -> backStack.add(ItineraryRoute(id)) },
                            onEarlier = planner::loadEarlier,
                            onLater = planner::loadLater,
                            onRetry = planner::retry,
                        )
                    }
                    entry<ItineraryRoute> { route ->
                        val journey = state.journey(route.journeyId)
                        if (journey == null) {
                            SessionFallback(wide, state.results) { backStack.pop(planner) }
                        } else {
                            // Scoped to this entry, so the times refresh only while the itinerary is open.
                            val lineTimes: LineTimesViewModel = koinViewModel()
                            val tracker: TripTrackerViewModel = koinViewModel()
                            LaunchedEffect(journey) {
                                lineTimes.show(journey)
                                tracker.show(journey)
                            }
                            val tracked = tracker.journey.collectAsStateWithLifecycle().value?.takeIf { it.id == journey.id }
                            ItineraryScreen(
                                wide = wide,
                                journey = tracked ?: journey,
                                now = now,
                                lineTimes = lineTimes.times.collectAsStateWithLifecycle().value,
                                onBack = { backStack.pop(planner) },
                                onWalk = { index ->
                                    following = true
                                    backStack.add(WalkRoute(journey.id, index))
                                },
                            )
                        }
                    }
                    entry<WalkRoute> { route ->
                        val leg = state.journey(route.journeyId)?.legs?.getOrNull(route.legIndex) as? Leg.Street
                        if (leg == null) {
                            SessionFallback(wide, state.results) { backStack.pop(planner) }
                        } else {
                            val position by userLocation.point.collectAsStateWithLifecycle()
                            WalkScreen(
                                wide = wide,
                                leg = leg,
                                position = position,
                                locationGranted = locationGranted,
                                following = following,
                                now = now,
                                onClose = { backStack.pop(planner) },
                                onRecenter = { following = true },
                                onRequestLocation = locationState::requestPermission,
                                onReplan = {
                                    planner.replanFromHere()
                                    while (backStack.last() != ResultsRoute && backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * Shown when a screen of the session has no journey, such as after process death. It waits while
 * the session plans again, then goes back: the journey is no longer in the results.
 */
@Composable
private fun SessionFallback(wide: Boolean, results: LoadState<*>, onBack: () -> Unit) {
    if (results == LoadState.Loading) {
        MapPanel(wide = wide, peekHeight = ItineraryPeekHeight) { LoadingState() }
    } else {
        LaunchedEffect(Unit) { onBack() }
    }
}

/** Gives no heading, so the compass sensor stays off outside the walking view. */
private object NoHeading : HeadingProvider {
    override fun updates(request: HeadingRequest) = emptyFlow<HeadingMeasurement>()
}

private fun Stop.toMapStop(): MapStop? = stopId?.let { MapStop(it, name, point) }

private fun NavBackStack<NavKey>.pop(planner: PlannerViewModel) {
    if (size <= 1) return
    val removed = removeAt(lastIndex)
    if (removed == ResultsRoute) planner.reset()
}
