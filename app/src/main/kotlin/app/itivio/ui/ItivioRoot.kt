package app.itivio.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.itivio.BuildConfig
import app.itivio.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.BoardingHint
import app.itivio.domain.BoardingRepository
import app.itivio.domain.Leg
import app.itivio.domain.hints
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.backPreviewDecorator
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.LocalPanelHeaderHeight
import app.itivio.ui.common.isWideLayout
import app.itivio.ui.common.panelInsets
import app.itivio.ui.explore.ExplorePeekHeight
import app.itivio.ui.disruptions.DisruptionsScreen
import app.itivio.ui.disruptions.DisruptionsViewModel
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.settings.AboutScreen
import app.itivio.ui.settings.SettingsScreen
import app.itivio.ui.settings.SettingsViewModel
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
import app.itivio.domain.WalkRoute as WalkPath
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
@Serializable data object DisruptionsRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object AboutRoute : NavKey

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
    // The walking view: the map turns with the walker unless the user asks for north up.
    var northUp by rememberSaveable { mutableStateOf(false) }
    var overviewRequest by remember { mutableIntStateOf(0) }
    var mapBearing by remember { mutableFloatStateOf(0f) }
    // Collected only while Explore is on top, so departures refresh only while they are visible.
    val nearbyState = if (top == ExploreRoute && locationGranted) {
        nearby.departures.collectAsStateWithLifecycle().value
    } else {
        LoadState.Idle
    }
    val areaStops = mapStops.stops.collectAsStateWithLifecycle().value.takeIf { top == ExploreRoute }.orEmpty()
    val now by rememberNow()

    var trackedJourney by remember { mutableStateOf<Journey?>(null) }
    val walkLeg = (top as? WalkRoute)?.let { route ->
        (trackedJourney?.takeIf { it.id == route.journeyId } ?: state.journey(route.journeyId))?.legs?.getOrNull(route.legIndex) as? Leg.Street
    }
    val mapJourney = when (top) {
        is ItineraryRoute -> trackedJourney?.takeIf { it.id == top.journeyId } ?: state.journey(top.journeyId)
        ResultsRoute -> state.highlighted
        // The walking view draws only its walk, from its start to its end.
        is WalkRoute -> walkLeg?.let { Journey("${top.journeyId}-walk-${top.legIndex}", listOf(it), it.start, it.end, 0) }
        else -> null
    }
    val walkPath = remember(walkLeg) { walkLeg?.let { WalkPath.of(it.steps, it.geometry) } }
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
        .flatMap { group -> group.departures.mapNotNull { departure -> departure.toMapStop() } }
        .distinctBy { it.stopId }

    fun openStop(stop: MapStop) {
        backStack.add(StopRoute(stop.stopId, stop.name, stop.point.latitude, stop.point.longitude))
    }

    // The screen on top measures its floating header, so the camera keeps routes below it.
    val headerHeight = remember { mutableStateOf(0.dp) }
    // Only the two top-level screens show the navigation bar or rail.
    val topLevel = top == ExploreRoute || top == DisruptionsRoute
    var navigationSize by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize()) {
        TransitMap(
            locationState = locationState,
            journey = mapJourney,
            destination = destinationPoint,
            stops = stops,
            insets = panelInsets(wide, peek, headerHeight.value, navigation = if (topLevel && wide) navigationSize else 0.dp),
            recenterRequest = recenterRequest,
            areaStops = areaStops,
            focus = stopPoint,
            follow = top is WalkRoute && following,
            walk = walkPath,
            northUp = northUp,
            overviewRequest = overviewRequest,
            onBearing = { mapBearing = it.toFloat() },
            onStop = ::openStop,
            onFollowStopped = { following = false },
            onCameraIdle = { box, zoom -> if (backStack.last() == ExploreRoute) mapStops.onCameraIdle(box, zoom) },
            modifier = Modifier.fillMaxSize(),
        )
        TopLevelNavigation(
            wide = wide,
            visible = topLevel,
            disruptions = top == DisruptionsRoute,
            onMap = { if (backStack.last() == DisruptionsRoute) backStack.pop(planner) },
            onDisruptions = { if (backStack.last() != DisruptionsRoute) backStack.add(DisruptionsRoute) },
            onSize = { navigationSize = it },
        ) {
            CompositionLocalProvider(LocalPanelHeaderHeight provides headerHeight) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.pop(planner) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                        remember { backPreviewDecorator() },
                    ),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                    // The back preview decorator gives the motion of a back gesture.
                    predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished },
                    entryProvider = entryProvider {
                        entry<ExploreRoute> {
                            // Scoped to this entry. A compact window shows the alerts and their count on
                            // home, so they load while home is open. A wide window has its own page.
                            val disruptions: DisruptionsViewModel = koinViewModel()
                            val homeDisruptions = if (wide) null else disruptions.disruptions.collectAsStateWithLifecycle().value
                            ExploreScreen(
                                wide = wide,
                                locationGranted = locationGranted,
                                nearby = nearbyState,
                                now = now,
                                onRequestLocation = locationState::requestPermission,
                                onRecenter = { recenterRequest++ },
                                onRetryNearby = nearby::retry,
                                disruptions = homeDisruptions,
                                onRetryDisruptions = disruptions::retry,
                                onDestination = { endpoint ->
                                    planner.setDestination(endpoint)
                                    backStack.add(ResultsRoute)
                                },
                                onStop = { location ->
                                    location.stopId?.let { openStop(MapStop(it, location.name, location.point)) }
                                },
                                onSettings = { backStack.add(SettingsRoute) },
                            )
                        }
                        entry<DisruptionsRoute> {
                            // Scoped to this entry, so the alerts load only while the page is open.
                            val disruptions: DisruptionsViewModel = koinViewModel()
                            DisruptionsScreen(
                                state = disruptions.disruptions.collectAsStateWithLifecycle().value,
                                now = now,
                                onRetry = disruptions::retry,
                                // A compact window has no rail. It gets here only when a wide window got narrow.
                                onBack = if (wide) null else ({ backStack.pop(planner) }),
                            )
                        }
                        entry<SettingsRoute> {
                            val settings: SettingsViewModel = koinViewModel()
                            SettingsScreen(
                                theme = settings.theme.collectAsStateWithLifecycle().value,
                                options = settings.options.collectAsStateWithLifecycle().value,
                                onBack = { backStack.pop(planner) },
                                onTheme = settings::setTheme,
                                onOptions = settings::setOptions,
                                onClearRecents = settings::clearRecents,
                                onDeleteAll = settings::deleteAll,
                                onAbout = { backStack.add(AboutRoute) },
                            )
                        }
                        entry<AboutRoute> {
                            AboutScreen(version = BuildConfig.VERSION_NAME, onBack = { backStack.pop(planner) })
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
                                    tracker.show(journey)
                                }
                                val tracked = tracker.journey.collectAsStateWithLifecycle().value?.takeIf { it.id == journey.id }
                                val visible = tracked ?: journey
                                LaunchedEffect(visible, state.time, state.arriveBy) {
                                    trackedJourney = visible
                                    lineTimes.show(visible, if (state.arriveBy) journey.departure.best else state.time)
                                }
                                // Read from the table in the app: no request. The legs of a changed train keep their stops.
                                val boardingRepository = koinInject<BoardingRepository>()
                                val boarding by produceState(emptyMap<Int, BoardingHint>(), journey, state.options.wheelchair) {
                                    value = boardingRepository.hints(journey, state.options.wheelchair)
                                }
                                ItineraryScreen(
                                    wide = wide,
                                    journey = visible,
                                    now = now,
                                    boarding = boarding,
                                    lineTimes = lineTimes.times.collectAsStateWithLifecycle().value,
                                    selection = tracker.selection.collectAsStateWithLifecycle().value,
                                    onSelectDeparture = tracker::selectDeparture,
                                    onResetTrain = tracker::resetTrain,
                                    onBack = { backStack.pop(planner) },
                                    onWalk = { index ->
                                        following = true
                                        backStack.add(WalkRoute(journey.id, index))
                                    },
                                )
                            }
                        }
                        entry<WalkRoute> { route ->
                            val leg = (trackedJourney?.takeIf { it.id == route.journeyId } ?: state.journey(route.journeyId))?.legs?.getOrNull(route.legIndex) as? Leg.Street
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
                                    northUp = northUp,
                                    bearing = { mapBearing },
                                    onNorthUp = {
                                        northUp = !northUp
                                        following = true
                                    },
                                    onOverview = {
                                        following = false
                                        overviewRequest++
                                    },
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
}

/**
 * The two top-level destinations of a wide window, on a navigation rail beside [content]. A
 * compact window has no bar: its home sheet holds the two pages. Only the top-level screens show
 * the rail ([visible]). The layout around [content] is the same in all cases, so the screens
 * keep their state. [onSize] gets the width of the rail.
 */
@Composable
private fun TopLevelNavigation(
    wide: Boolean,
    visible: Boolean,
    disruptions: Boolean,
    onMap: () -> Unit,
    onDisruptions: () -> Unit,
    onSize: (Dp) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rail = visible && wide
    Row(Modifier.fillMaxSize()) {
        if (rail) {
            NavigationRail(Modifier.onSizeChanged { onSize(with(density) { it.width.toDp() }) }) {
                NavigationRailItem(
                    selected = !disruptions,
                    onClick = onMap,
                    icon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                    label = { Text(stringResource(R.string.navigation_map)) },
                )
                NavigationRailItem(
                    selected = disruptions,
                    onClick = onDisruptions,
                    icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
                    label = { Text(stringResource(R.string.disruptions_title)) },
                )
            }
        }
        // The rail takes the system inset on its side; the screens must not add it again.
        val insets = if (rail) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier
        Box(Modifier.weight(1f).fillMaxHeight().then(insets)) { content() }
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

/** The departures are in time order, so a stop gets the line of its next departure. */
private fun Departure.toMapStop(): MapStop? = stop.stopId?.let { MapStop(it, stop.name, stop.point, line, mode) }

private fun NavBackStack<NavKey>.pop(planner: PlannerViewModel) {
    if (size <= 1) return
    val removed = removeAt(lastIndex)
    if (removed == ResultsRoute) planner.reset()
}
