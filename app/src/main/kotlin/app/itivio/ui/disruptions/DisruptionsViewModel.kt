package app.itivio.ui.disruptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.Disruption
import app.itivio.domain.GeoPoint
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.disruptionsOf
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.closeTogether
import app.itivio.ui.common.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** The disruptions on the saved stops, then the other disruptions on the stops near the device. */
data class DisruptionGroups(val saved: List<Disruption> = emptyList(), val nearby: List<Disruption> = emptyList()) {
    val isEmpty: Boolean get() = saved.isEmpty() && nearby.isEmpty()
}

/**
 * Service alerts for the saved stops (Home, Work, and favorites) and for the stops near the
 * device. The provider has no list of all disruptions, so the alerts come with the departures of
 * each stop. Loads only while the screen collects [disruptions]. A failed refresh keeps the last
 * list; only the first load shows an error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisruptionsViewModel(
    private val repository: RoutingRepository,
    places: PlacesRepository,
    userLocation: UserLocation,
) : ViewModel() {
    private val retries = MutableStateFlow(0)
    private var shown: DisruptionGroups? = null

    val disruptions: StateFlow<LoadState<DisruptionGroups>> =
        combine(
            // A storage failure leaves the stops near the device.
            places.saved.map { it.stopIds() }.catch { emit(emptyList()) }.distinctUntilChanged(),
            userLocation.point.distinctUntilChanged { a, b -> closeTogether(a, b, MOVE_METERS) },
            retries,
        ) { stops, point, _ -> stops to point }
            .transformLatest { (stops, point) ->
                while (true) {
                    if (shown == null) emit(LoadState.Loading)
                    val (groups, failure) = load(stops, point)
                    if (groups != null) {
                        // An empty state before the first location fix is not a successful request.
                        shown = groups.takeIf { stops.isNotEmpty() || point != null }
                        emit(LoadState.Ready(groups))
                    } else if (shown == null && failure != null) {
                        emit(LoadState.Failed(failure))
                        return@transformLatest
                    }
                    delay(REFRESH_MILLIS)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Idle)

    fun retry() {
        retries.value++
    }

    /** The groups when one request or more gave an answer, or when there was nothing to ask. Else the failure. */
    private suspend fun load(stops: List<String>, point: GeoPoint?): Pair<DisruptionGroups?, Failure?> = coroutineScope {
        val saved = stops.map { stopId -> async { attempt { repository.stopDepartures(stopId, STOP_SCAN, withAlerts = true) } } }
        val nearby = point?.let { async { attempt { repository.nearbyDepartures(it, withAlerts = true) } } }
        val savedResults = saved.awaitAll()
        val nearbyResult = nearby?.await()
        val results = savedResults + listOfNotNull(nearbyResult)
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.toFailure()
        if (results.isNotEmpty() && results.none { it.isSuccess }) return@coroutineScope null to failure
        val onSaved = disruptionsOf(savedResults.flatMap { it.getOrDefault(emptyList()) })
        val onNearby = disruptionsOf(nearbyResult?.getOrNull().orEmpty(), shown = onSaved.map { it.alert }.toSet())
        DisruptionGroups(onSaved, onNearby) to failure
    }

    /** Like `runCatching`, but a cancellation stops the load. */
    private suspend fun attempt(request: suspend () -> List<Departure>): Result<List<Departure>> = try {
        Result.success(request())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        /** Alerts change slowly; five minutes keeps the list current without heavy polling. */
        const val REFRESH_MILLIS = 300_000L

        /** Moves shorter than this keep the same stops, so they do not trigger a new request. */
        const val MOVE_METERS = 150.0

        /** One request goes out for each saved stop, so the number of stops has a limit. */
        const val MAX_SAVED_STOPS = 10

        /** Alerts come with departures; this many departures show the lines of a stop. */
        private const val STOP_SCAN = 20

        /** The IDs of the saved stops: Home, Work, then the favorites. Places that are not stops have no alerts. */
        internal fun SavedPlaces.stopIds(): List<String> =
            (listOfNotNull(home, work) + favorites).mapNotNull { it.stopId }.distinct().take(MAX_SAVED_STOPS)
    }
}
