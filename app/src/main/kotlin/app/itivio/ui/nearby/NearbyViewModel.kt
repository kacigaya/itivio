package app.itivio.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.RoutingRepository
import app.itivio.domain.WalkRoute
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.closeTogether
import app.itivio.ui.common.toFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Departures from one stop, in time order. [distanceMeters] is the straight-line distance from
 * the device to the nearest platform, or null without a position.
 */
data class StopDepartures(val name: String, val departures: List<Departure>, val distanceMeters: Double? = null)

/**
 * Upcoming departures around the device. Refreshes while the screen is visible, and again when
 * the user moves far enough to change the set of nearby stops. A failed background refresh keeps
 * the last list; only the first load shows an error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModel(
    private val repository: RoutingRepository,
    userLocation: UserLocation,
) : ViewModel() {
    private val retries = MutableStateFlow(0)
    private var shown: List<StopDepartures>? = null

    val departures: StateFlow<LoadState<List<StopDepartures>>> =
        combine(userLocation.point.distinctUntilChanged { a, b -> closeTogether(a, b, MOVE_METERS) }, retries) { point, _ -> point }
            .transformLatest { point ->
                if (point == null) {
                    emit(LoadState.Idle)
                    return@transformLatest
                }
                while (true) {
                    if (shown == null) emit(LoadState.Loading)
                    val result = runCatching { repository.nearbyDepartures(point) }
                    val failure = result.exceptionOrNull()?.toFailure()
                    if (failure == null) {
                        val groups = group(result.getOrThrow(), from = point)
                        shown = groups
                        emit(LoadState.Ready(groups))
                    } else if (shown == null) {
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

    companion object {
        /** Live times change slowly; once a minute keeps the list fresh without heavy polling. */
        const val REFRESH_MILLIS = 60_000L

        /** Moves shorter than this keep the same stops, so they do not trigger a new request. */
        const val MOVE_METERS = 150.0

        /** Groups by stop name, so platforms of one station share a card. [from] is the device position. */
        fun group(departures: List<Departure>, from: GeoPoint? = null): List<StopDepartures> =
            departures.sortedBy { it.time.best }
                .groupBy { it.stop.name }
                .map { (name, list) ->
                    StopDepartures(name, list, from?.let { here -> list.minOf { WalkRoute.distance(here, it.stop.point) } })
                }
    }
}
