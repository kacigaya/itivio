package app.itivio.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.itivio.domain.GeoPoint
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.isWideLayout
import app.itivio.ui.common.panelInsets
import app.itivio.ui.explore.ExplorePeekHeight
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.itinerary.ItineraryPeekHeight
import app.itivio.ui.itinerary.ItineraryScreen
import app.itivio.ui.map.TransitMap
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.results.ResultsPeekHeight
import app.itivio.ui.results.ResultsScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState

@Serializable data object ExploreRoute : NavKey
@Serializable data object ResultsRoute : NavKey
@Serializable data class ItineraryRoute(val journeyId: String) : NavKey

/**
 * One map stays alive behind all screens; each screen draws its sheet or pane over it. The
 * planner ViewModel is scoped to the activity so every screen reads the same session.
 */
@Composable
fun ItivioRoot(planner: PlannerViewModel = koinViewModel()) {
    val backStack = rememberNavBackStack(ExploreRoute)
    val state by planner.state.collectAsStateWithLifecycle()
    val wide = isWideLayout()
    val userLocation = koinInject<UserLocation>()

    val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())
    val locationGranted = locationState.permission is LocationPermission.Granted
    LaunchedEffect(locationState) {
        snapshotFlow { locationState.lastLocation?.position }.collect { position ->
            userLocation.point.value = position?.let { GeoPoint(it.latitude, it.longitude) }
        }
    }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }

    val top = backStack.last()
    val mapJourney = when (top) {
        is ItineraryRoute -> state.journey(top.journeyId)
        ResultsRoute -> state.highlighted
        else -> null
    }
    val peek = when (top) {
        ResultsRoute -> ResultsPeekHeight
        is ItineraryRoute -> ItineraryPeekHeight
        else -> ExplorePeekHeight
    }
    val destinationPoint = (state.destination as? Endpoint.Place)?.location?.point?.takeIf { top != ExploreRoute }

    Box(Modifier.fillMaxSize()) {
        TransitMap(
            locationState = locationState,
            journey = mapJourney,
            destination = destinationPoint,
            insets = panelInsets(wide, peek),
            recenterRequest = recenterRequest,
            modifier = Modifier.fillMaxSize(),
        )
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
                        onRequestLocation = locationState::requestPermission,
                        onRecenter = { recenterRequest++ },
                        onDestination = { endpoint ->
                            planner.setDestination(endpoint)
                            backStack.add(ResultsRoute)
                        },
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
                        onSort = planner::setSort,
                        onSelect = planner::select,
                        onOpen = { id -> backStack.add(ItineraryRoute(id)) },
                        onLater = planner::loadLater,
                        onRetry = planner::retry,
                    )
                }
                entry<ItineraryRoute> { route ->
                    val journey = state.journey(route.journeyId)
                    if (journey == null) {
                        // The session was reset (e.g. process death); fall back to the start.
                        LaunchedEffect(Unit) { backStack.pop(planner) }
                    } else {
                        ItineraryScreen(wide = wide, journey = journey, onBack = { backStack.pop(planner) })
                    }
                }
            },
        )
    }
}

private fun NavBackStack<NavKey>.pop(planner: PlannerViewModel) {
    if (size <= 1) return
    val removed = removeAt(lastIndex)
    if (removed == ResultsRoute) planner.reset()
}
