package app.itivio.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.RoutingRepository
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.toFailure
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A journey end: the device position, resolved when planning, or a picked place. */
sealed interface Endpoint {
    data object MyLocation : Endpoint
    data class Place(val location: Location) : Endpoint
}

enum class RouteSort { BEST, FASTEST, FEWEST_TRANSFERS, LEAST_WALKING }

data class PlannerState(
    val origin: Endpoint = Endpoint.MyLocation,
    val destination: Endpoint? = null,
    /** Null means "leave now". */
    val time: Instant? = null,
    val arriveBy: Boolean = false,
    val results: LoadState<List<Journey>> = LoadState.Idle,
    val sort: RouteSort = RouteSort.BEST,
    val loadingLater: Boolean = false,
    val canLoadLater: Boolean = false,
    val selectedId: String? = null,
) {
    val journeys: List<Journey> get() = (results as? LoadState.Ready)?.value.orEmpty()

    val sortedJourneys: List<Journey>
        get() = when (sort) {
            RouteSort.BEST -> journeys
            RouteSort.FASTEST -> journeys.sortedBy { it.duration }
            RouteSort.FEWEST_TRANSFERS -> journeys.sortedWith(compareBy({ it.transfers }, { it.duration }))
            RouteSort.LEAST_WALKING -> journeys.sortedWith(compareBy({ it.walkingMeters }, { it.duration }))
        }

    /** The journey the map draws: the explicit selection, else the first card. */
    val highlighted: Journey? get() = journeys.firstOrNull { it.id == selectedId } ?: sortedJourneys.firstOrNull()

    fun journey(id: String): Journey? = journeys.firstOrNull { it.id == id }
}

/**
 * Holds one planning session: endpoints, results, and selection. Shared by the search, results,
 * and itinerary screens so the map always shows the same journey the sheet describes.
 */
class PlannerViewModel(
    private val repository: RoutingRepository,
    private val userLocation: UserLocation,
) : ViewModel() {
    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    private var planJob: Job? = null
    private var laterCursor: String? = null
    private var lastRequest: JourneyRequest? = null

    fun setOrigin(endpoint: Endpoint) {
        _state.update { it.copy(origin = endpoint) }
        planIfReady()
    }

    fun setDestination(endpoint: Endpoint) {
        _state.update { it.copy(destination = endpoint) }
        planIfReady()
    }

    fun swap() {
        val current = _state.value
        val destination = current.destination ?: return
        _state.update { it.copy(origin = destination, destination = current.origin) }
        planIfReady()
    }

    /** Sets when to travel. A null [time] means "leave now"; [arriveBy] then has no meaning. */
    fun setTime(time: Instant?, arriveBy: Boolean) {
        _state.update { it.copy(time = time, arriveBy = time != null && arriveBy) }
        planIfReady()
    }

    fun setSort(sort: RouteSort) = _state.update { it.copy(sort = sort) }

    fun select(journeyId: String) = _state.update { it.copy(selectedId = journeyId) }

    fun retry() = planIfReady()

    /** Clears the session when the user leaves the results. Keeps the origin choice; the time goes back to now. */
    fun reset() {
        planJob?.cancel()
        laterCursor = null
        lastRequest = null
        _state.update { PlannerState(origin = it.origin) }
    }

    private fun planIfReady() {
        val current = _state.value
        val destination = current.destination ?: return
        planJob?.cancel()
        laterCursor = null
        val from = resolve(current.origin)
        val to = resolve(destination)
        if (from == null || to == null) {
            _state.update { it.copy(results = LoadState.Failed(Failure.NO_LOCATION), canLoadLater = false) }
            return
        }
        val request = JourneyRequest(from = from, to = to, time = current.time, arriveBy = current.arriveBy)
        lastRequest = request
        _state.update { it.copy(results = LoadState.Loading, selectedId = null, canLoadLater = false, loadingLater = false) }
        planJob = viewModelScope.launch {
            val result = runCatching { repository.planJourneys(request) }
            result.onSuccess { page ->
                laterCursor = page.laterCursor
                _state.update { it.copy(results = LoadState.Ready(page.journeys), canLoadLater = page.laterCursor != null) }
            }.onFailure { error ->
                _state.update { it.copy(results = LoadState.Failed(error.toFailure())) }
            }
        }
    }

    fun loadLater() {
        val request = lastRequest ?: return
        val cursor = laterCursor ?: return
        if (_state.value.loadingLater) return
        _state.update { it.copy(loadingLater = true) }
        planJob = viewModelScope.launch {
            val result = runCatching { repository.planJourneys(request, cursor) }
            result.onSuccess { page ->
                laterCursor = page.laterCursor
                _state.update { state ->
                    val known = state.journeys.map { it.id }.toSet()
                    val merged = state.journeys + page.journeys.filterNot { it.id in known }
                    state.copy(results = LoadState.Ready(merged), loadingLater = false, canLoadLater = page.laterCursor != null)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                // Keep the shown journeys; the button stays so the user can try again.
                _state.update { it.copy(loadingLater = false) }
            }
        }
    }

    private fun resolve(endpoint: Endpoint): Location? = when (endpoint) {
        is Endpoint.Place -> endpoint.location
        Endpoint.MyLocation -> userLocation.point.value?.let(::myLocation)
    }

    private fun myLocation(point: GeoPoint) =
        Location(id = MY_LOCATION_ID, name = "", kind = LocationKind.PLACE, point = point)

    companion object {
        const val MY_LOCATION_ID = "itivio:my-location"
    }
}
