package app.itivio.ui

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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.itivio.BuildConfig
import app.itivio.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.Journey
import app.itivio.domain.BoardingHint
import app.itivio.domain.BoardingRepository
import app.itivio.domain.Leg
import app.itivio.domain.hints
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.ui.common.DisruptionsPage
import app.itivio.ui.common.FullScreenPage
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.ScreenChange
import app.itivio.ui.common.screenMotionDecorator
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.LocalPanelHeaderHeight
import app.itivio.ui.common.isWideLayout
import app.itivio.ui.common.keptWhileLeaving
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
import app.itivio.ui.map.toFix
import app.itivio.ui.walk.WalkRerouteViewModel
import app.itivio.ui.nearby.NearbyViewModel
import app.itivio.ui.nearby.rememberNow
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.results.ResultsPeekHeight
import app.itivio.ui.results.ResultsPlacementMillis
import app.itivio.ui.results.ResultsScreen
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.stop.StopDeparturesViewModel
import app.itivio.ui.stop.StopPeekHeight
import app.itivio.ui.stop.StopScreen
import app.itivio.ui.walk.WalkPeekHeight
import app.itivio.ui.walk.WalkScreen
import app.itivio.domain.WalkProgress
import app.itivio.domain.WalkRoute as WalkPath
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.HeadingMeasurement
import org.maplibre.compose.location.HeadingProvider
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.compose.location.LocationAccuracy
import org.maplibre.compose.location.LocationRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration.Companion.milliseconds
import org.maplibre.spatialk.units.extensions.meters
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
    rerouter: WalkRerouteViewModel = koinViewModel(),
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
    val walking = top is WalkRoute
    val locationState = rememberLocationState(
        provider = rememberDefaultLocationProvider(),
        request = if (walking) WalkLocationRequest else LocationRequest.Standard,
        headingProvider = if (walking) heading else NoHeading,
        headingRequest = WalkHeadingRequest,
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
    val mapShowsExplore = top == ExploreRoute || top == DisruptionsRoute
    val areaStops = mapStops.stops.collectAsStateWithLifecycle().value.takeIf { mapShowsExplore }.orEmpty()
    val now by rememberNow()

    var trackedJourney by remember { mutableStateOf<Journey?>(null) }
    fun journeyFor(id: String) = trackedJourney?.takeIf { it.id == id } ?: state.journey(id)
    val reroute by rerouter.reroute.collectAsStateWithLifecycle()
    val plannedWalkLeg = (top as? WalkRoute)?.let { route ->
        journeyFor(route.journeyId)?.legs?.getOrNull(route.legIndex) as? Leg.Street
    }
    val walkKey = (top as? WalkRoute)?.let { route -> plannedWalkLeg?.let { rerouteKey(route, it, state.options) } }
    val walkLeg = plannedWalkLeg?.rerouted(walkKey, reroute)
    val mapJourney = when (top) {
        is ItineraryRoute -> journeyFor(top.journeyId)
        ResultsRoute -> {
            val highlighted = state.highlighted
            var shown by remember { mutableStateOf(state.sort to highlighted) }
            LaunchedEffect(state.sort, highlighted) {
                val (shownSort, shownJourney) = shown
                if (shownSort != state.sort && shownJourney != null && highlighted != null && shownJourney.id != highlighted.id) {
                    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                    delay((ResultsPlacementMillis * scale).toLong())
                }
                shown = state.sort to highlighted
            }
            shown.second
        }
        // The walking view draws only its walk, from its start to its end.
        is WalkRoute -> walkLeg?.let { Journey("${top.journeyId}-walk-${top.legIndex}", listOf(it), it.start, it.end, 0) }
        else -> null
    }
    val walkPath = remember(walkLeg) { walkLeg?.let { WalkPath.of(it.steps, it.geometry) } }
    var walkProgress by remember(walkPath) { mutableStateOf<WalkProgress?>(null) }
    LaunchedEffect(walkPath, locationState) {
        val path = walkPath ?: return@LaunchedEffect
        snapshotFlow { locationState.lastLocation?.toFix() }.collect { fix ->
            walkProgress = fix?.let { path.progress(it, wasOffRoute = walkProgress?.isOffRoute == true) }
        }
    }
    val offRoute = walkProgress?.isOffRoute == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(offRoute, walkKey, walkLeg, lifecycle) {
        val key = walkKey ?: return@LaunchedEffect
        val leg = walkLeg ?: return@LaunchedEffect
        if (!offRoute) return@LaunchedEffect
        val options = state.options
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) {
                    locationState.lastLocation?.position?.let { rerouter.reroute(key, leg, GeoPoint(it.latitude, it.longitude), options) }
                    delay(WalkRerouteViewModel.MIN_INTERVAL.toMillis())
                }
            } finally {
                rerouter.cancel()
            }
        }
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
    var lastNearby by remember { mutableStateOf(nearbyState) }
    if (top == ExploreRoute) SideEffect { lastNearby = nearbyState }
    val mapNearby = if (top == DisruptionsRoute && locationGranted) lastNearby else nearbyState
    val stops = remember(mapNearby) {
        (mapNearby as? LoadState.Ready)?.value.orEmpty()
            .flatMap { group -> group.departures.mapNotNull { departure -> departure.toMapStop() } }
            .distinctBy { it.stopId }
    }

    fun openStop(stop: MapStop) {
        backStack.add(StopRoute(stop.stopId, stop.name, stop.point.latitude, stop.point.longitude))
    }

    // The screen on top measures its floating header, so the camera keeps routes below it.
    val headerHeight = remember { mutableStateOf(0.dp) }
    // Only the two top-level screens show the navigation bar or rail.
    val topLevel = top == ExploreRoute || top == DisruptionsRoute
    var navigationSize by remember { mutableStateOf(0.dp) }
    val screenChange = remember { ScreenChange() }

    Box(Modifier.fillMaxSize()) {
        TransitMap(
            locationState = locationState,
            journey = mapJourney,
            results = top == ResultsRoute,
            destination = destinationPoint,
            stops = stops,
            insets = panelInsets(wide, peek, headerHeight.value, navigation = if (topLevel && wide) navigationSize else 0.dp),
            recenterRequest = recenterRequest,
            areaStops = areaStops,
            focus = stopPoint,
            follow = top is WalkRoute && following,
            walk = walkPath,
            walkProgress = walkProgress,
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
                        remember { screenMotionDecorator(screenChange) },
                    ),
                    // The screen motion decorator gives the motion of all screen changes.
                    transitionSpec = { screenChange.transform(this, back = false) },
                    popTransitionSpec = { screenChange.transform(this, back = true) },
                    predictivePopTransitionSpec = { screenChange.transform(this, back = true) },
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
                        entry<DisruptionsRoute>(metadata = DisruptionsPage) {
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
                        entry<SettingsRoute>(metadata = FullScreenPage) {
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
                        entry<AboutRoute>(metadata = FullScreenPage) {
                            AboutScreen(version = BuildConfig.VERSION_NAME, onBack = { backStack.pop(planner) })
                        }
                        entry<StopRoute> { route ->
                            // Scoped to this entry, so the board refreshes only while it is open.
                            val board: StopDeparturesViewModel = koinViewModel()
                            val places: SavedPlacesViewModel = koinViewModel()
                            LaunchedEffect(route.stopId) { board.show(route.stopId) }
                            val stop = Location(
                                route.stopId, route.name, LocationKind.STOP,
                                GeoPoint(route.latitude, route.longitude), stopId = route.stopId,
                            )
                            StopScreen(
                                wide = wide,
                                name = route.name,
                                departures = board.departures.collectAsStateWithLifecycle().value,
                                now = now,
                                favorite = places.saved.collectAsStateWithLifecycle().value?.isFavorite(stop),
                                onBack = { backStack.pop(planner) },
                                onDirections = {
                                    planner.setDestination(Endpoint.Place(stop))
                                    backStack.add(ResultsRoute)
                                },
                                onToggleFavorite = { places.toggleFavorite(stop) },
                                onRetry = board::retry,
                            )
                        }
                        entry<ResultsRoute> {
                            ResultsScreen(
                                wide = wide,
                                state = keptWhileLeaving(state),
                                now = now,
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
                                // Null until the table loads, so the itinerary can show its extras together.
                                val boarding by produceState<Map<Int, BoardingHint>?>(null, journey, state.options.wheelchair) {
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
                            val leg = (journeyFor(route.journeyId)?.legs?.getOrNull(route.legIndex) as? Leg.Street)
                                ?.let { it.rerouted(rerouteKey(route, it, state.options), reroute) }
                            if (leg == null) {
                                SessionFallback(wide, state.results) { backStack.pop(planner) }
                            } else {
                                WalkScreen(
                                    wide = wide,
                                    leg = leg,
                                    progress = keptWhileLeaving(walkProgress),
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

private val WalkLocationRequest = LocationRequest(LocationRequest.Standard) {
    accuracy = LocationAccuracy.BestForNavigation
    minimumInterval = 500.milliseconds
    minimumDistance = 0.meters
}

private val WalkHeadingRequest = HeadingRequest(HeadingRequest.Standard) { minimumInterval = 200.milliseconds }

private fun rerouteKey(route: WalkRoute, planned: Leg.Street, options: JourneyOptions): Any =
    listOf(route, planned.to.stopId, planned.to.point, options)

private fun Leg.Street.rerouted(key: Any?, reroute: WalkRerouteViewModel.Reroute?): Leg.Street =
    reroute?.takeIf { key != null && it.key == key }?.leg ?: this

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
