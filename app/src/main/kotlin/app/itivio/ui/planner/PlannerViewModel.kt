package app.itivio.ui.planner

import androidx.lifecycle.SavedStateHandle
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
import app.itivio.domain.TransitMode
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
 *
 * [savedState] keeps only the request: the endpoints and the travel time. After process death,
 * the session plans again from it. The results, the sort, and the selection are not kept.
 */
class PlannerViewModel(
    private val repository: RoutingRepository,
    private val userLocation: UserLocation,
    private val optionsRepository: JourneyOptionsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    private var planJob: Job? = null
    private var pageJob: Job? = null
    private var earlierCursor: String? = null
    private var laterCursor: String? = null
    private var lastRequest: JourneyRequest? = null
    private var restoreJob: Job? = null

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

    init {
        restore()
        // The settings screen also edits the stored options. The first value is the read above.
        viewModelScope.launch {
            try {
                optionsRepository.options.drop(1).collect { stored -> _state.update { it.copy(options = stored) } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Storage failed: the options of this session stay.
            }
        }
    }

    private fun restore() {
        val saved = savedState.get<String>(SAVED_REQUEST)?.let { text ->
            try {
                json.decodeFromString<SavedRequest>(text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return
        _state.update {
            it.copy(
                origin = saved.origin.toEndpoint(),
                destination = saved.destination.toEndpoint(),
                time = saved.time?.let(Instant::ofEpochMilli),
                arriveBy = saved.arriveBy,
                results = LoadState.Loading,
            )
        }
        restoreJob = viewModelScope.launch {
            // The first location fix comes some seconds after the app starts again.
            if (Endpoint.MyLocation in listOf(_state.value.origin, _state.value.destination)) {
                withTimeoutOrNull(LOCATION_WAIT_MILLIS) { userLocation.point.filterNotNull().first() }
            }
            restoreJob = null
            planIfReady()
        }
    }

    private fun save(state: PlannerState) {
        val destination = state.destination
        if (destination == null) {
            savedState.remove<String>(SAVED_REQUEST)
            return
        }
        val request = SavedRequest(state.origin.toSaved(), destination.toSaved(), state.time?.toEpochMilli(), state.arriveBy)
        savedState[SAVED_REQUEST] = json.encodeToString(request)
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
        savedState.remove<String>(SAVED_REQUEST)
    }

    /**
     * Plans again from the device position to the same destination, leaving now. This is the
     * only action of the walking view that sends the position.
     */
    fun replanFromHere() {
        _state.update { it.copy(origin = Endpoint.MyLocation, time = null, arriveBy = false) }
        planIfReady()
    }

    private fun cancelRequests() {
        restoreJob?.cancel()
        restoreJob = null
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
        save(current)
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

        private const val SAVED_REQUEST = "planner.request"
        private const val LOCATION_WAIT_MILLIS = 10_000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** The part of a session that survives process death. A null place is the device position. */
@Serializable
private data class SavedRequest(val origin: SavedPlace?, val destination: SavedPlace?, val time: Long?, val arriveBy: Boolean)

@Serializable
private data class SavedPlace(
    val id: String,
    val name: String,
    val kind: LocationKind,
    val latitude: Double,
    val longitude: Double,
    val area: String? = null,
    val stopId: String? = null,
    val modes: Set<TransitMode> = emptySet(),
)

private fun Endpoint.toSaved(): SavedPlace? = (this as? Endpoint.Place)?.location?.let {
    SavedPlace(it.id, it.name, it.kind, it.point.latitude, it.point.longitude, it.area, it.stopId, it.modes)
}

private fun SavedPlace?.toEndpoint(): Endpoint =
    this?.let { Endpoint.Place(Location(id, name, kind, GeoPoint(latitude, longitude), area, stopId, modes)) } ?: Endpoint.MyLocation
