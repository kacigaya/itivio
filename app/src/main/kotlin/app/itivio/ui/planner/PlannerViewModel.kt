package app.itivio.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A journey end: the device position, resolved when planning, or a picked place. */
sealed interface Endpoint {
    data object MyLocation : Endpoint
    data class Place(val location: Location) : Endpoint
}

enum class RouteSort { BEST, FASTEST, FEWEST_TRANSFERS, LEAST_WALKING }

/** A page of journeys before or after the ones on screen. */
enum class Paging { EARLIER, LATER }

data class PlannerState(
    val origin: Endpoint = Endpoint.MyLocation,
    val destination: Endpoint? = null,
    /** Null means "leave now". */
    val time: Instant? = null,
    val arriveBy: Boolean = false,
    val options: JourneyOptions = JourneyOptions(),
    val optionsLoaded: Boolean = false,
    val results: LoadState<List<Journey>> = LoadState.Idle,
    val sort: RouteSort = RouteSort.BEST,
    /** The page that loads now. Only one page loads at a time. */
    val loadingPage: Paging? = null,
    val canLoadEarlier: Boolean = false,
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
    private val optionsRepository: JourneyOptionsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    private var planJob: Job? = null
    private var pageJob: Job? = null
    private var earlierCursor: String? = null
    private var laterCursor: String? = null
    private var lastRequest: JourneyRequest? = null

    // Stored options are optional: a storage failure, such as a full disk, must not stop routing.
    // A failed read keeps the defaults, and a failed write keeps the options for this session.
    private val storedOptions: Job = viewModelScope.launch {
        val stored = try {
            optionsRepository.options.first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(optionsLoaded = true) }
            return@launch
        }
        _state.update { it.copy(options = stored, optionsLoaded = true) }
    }

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

    /** Applies [options], keeps them for the next app start, and plans again. */
    fun setOptions(options: JourneyOptions) {
        if (options == _state.value.options) return
        // A late read from storage must not replace the newer choice.
        storedOptions.cancel()
        _state.update { it.copy(options = options, optionsLoaded = true) }
        viewModelScope.launch {
            try {
                optionsRepository.save(options)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the options still apply until the app stops.
            }
        }
        planIfReady()
    }

    fun setSort(sort: RouteSort) = _state.update { it.copy(sort = sort) }

    fun select(journeyId: String) = _state.update { it.copy(selectedId = journeyId) }

    fun retry() = planIfReady()

    /**
     * Clears the session when the user leaves the results. Keeps the origin choice and the
     * journey options; the time goes back to now.
     */
    fun reset() {
        cancelRequests()
        _state.update { PlannerState(origin = it.origin, options = it.options, optionsLoaded = it.optionsLoaded) }
    }

    private fun cancelRequests() {
        planJob?.cancel()
        pageJob?.cancel()
        earlierCursor = null
        laterCursor = null
        lastRequest = null
    }

    private fun planIfReady() {
        val current = _state.value
        val destination = current.destination ?: return
        cancelRequests()
        val from = resolve(current.origin)
        val to = resolve(destination)
        if (from == null || to == null) {
            _state.update {
                it.copy(results = LoadState.Failed(Failure.NO_LOCATION), canLoadEarlier = false, canLoadLater = false, loadingPage = null)
            }
            return
        }
        _state.update {
            it.copy(results = LoadState.Loading, selectedId = null, canLoadEarlier = false, canLoadLater = false, loadingPage = null)
        }
        planJob = viewModelScope.launch {
            // The first plan can start before the read of the stored options ends.
            storedOptions.join()
            val request = JourneyRequest(from, to, current.time, current.arriveBy, _state.value.options)
            lastRequest = request
            val result = runCatching { repository.planJourneys(request) }
            result.onSuccess { page ->
                earlierCursor = page.earlierCursor
                laterCursor = page.laterCursor
                _state.update {
                    it.copy(
                        results = LoadState.Ready(page.journeys),
                        canLoadEarlier = page.earlierCursor != null,
                        canLoadLater = page.laterCursor != null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(results = LoadState.Failed(error.toFailure())) }
            }
        }
    }

    fun loadEarlier() = loadPage(Paging.EARLIER)

    fun loadLater() = loadPage(Paging.LATER)

    private fun loadPage(paging: Paging) {
        val request = lastRequest ?: return
        val cursor = (if (paging == Paging.EARLIER) earlierCursor else laterCursor) ?: return
        if (_state.value.loadingPage != null) return
        _state.update { it.copy(loadingPage = paging) }
        pageJob = viewModelScope.launch {
            val result = runCatching { repository.planJourneys(request, cursor) }
            result.onSuccess { page ->
                // Each page has cursors for both directions. Only the one that continues this direction is new.
                if (paging == Paging.EARLIER) earlierCursor = page.earlierCursor else laterCursor = page.laterCursor
                _state.update { state ->
                    val known = state.journeys.map { it.id }.toSet()
                    val added = page.journeys.filterNot { it.id in known }
                    state.copy(
                        results = LoadState.Ready(if (paging == Paging.EARLIER) added + state.journeys else state.journeys + added),
                        // A new journey can sort first; the map keeps the journey it showed.
                        selectedId = state.selectedId ?: state.highlighted?.id,
                        loadingPage = null,
                        canLoadEarlier = earlierCursor != null,
                        canLoadLater = laterCursor != null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                // Keep the shown journeys; the button stays so the user can try again.
                _state.update { it.copy(loadingPage = null) }
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
