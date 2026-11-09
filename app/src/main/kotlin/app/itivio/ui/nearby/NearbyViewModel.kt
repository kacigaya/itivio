package app.itivio.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.RoutingRepository
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
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
import kotlin.math.cos
import kotlin.math.sqrt

/** Departures from one stop, in time order. */
data class StopDepartures(val name: String, val departures: List<Departure>)

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
        combine(userLocation.point.distinctUntilChanged(::closeTogether), retries) { point, _ -> point }
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
                        val groups = group(result.getOrThrow())
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

        /** Groups by stop name, so platforms of one station share a heading. */
        fun group(departures: List<Departure>): List<StopDepartures> =
            departures.sortedBy { it.time.best }
                .groupBy { it.stop.name }
                .map { (name, list) -> StopDepartures(name, list) }

        private fun closeTogether(a: GeoPoint?, b: GeoPoint?): Boolean {
            if (a == null || b == null) return a == b
            // Equirectangular distance: exact enough below a few kilometres.
            val metersPerDegree = 111_320.0
            val dy = (a.latitude - b.latitude) * metersPerDegree
            val dx = (a.longitude - b.longitude) * metersPerDegree * cos(Math.toRadians(a.latitude))
            return sqrt(dx * dx + dy * dy) < MOVE_METERS
        }
    }
}
