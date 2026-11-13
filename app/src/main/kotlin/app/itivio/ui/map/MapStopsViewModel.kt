package app.itivio.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.BoundingBox
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The stops in the map area, so the user can tap any stop. Loads only at street zoom, after the
 * camera stops, and only when the area leaves the one already loaded. A failed request keeps the
 * stops on the map and shows no error: the stops are an extra.
 */
@OptIn(FlowPreview::class)
class MapStopsViewModel(private val repository: RoutingRepository) : ViewModel() {
    private data class View(val box: BoundingBox, val zoom: Double)

    private val view = MutableStateFlow<View?>(null)
    private val _stops = MutableStateFlow<List<MapStop>>(emptyList())
    val stops: StateFlow<List<MapStop>> = _stops.asStateFlow()

    private var loaded: BoundingBox? = null

    init {
        viewModelScope.launch {
            view.filterNotNull().debounce(DEBOUNCE_MILLIS).collectLatest { (box, zoom) ->
                if (zoom < MAP_STOPS_MIN_ZOOM || box.north - box.south > MAX_SPAN || box.east - box.west > MAX_SPAN) return@collectLatest
                if (loaded?.contains(box) == true) return@collectLatest
                // A larger, rounded area: small moves stay inside it, and the request shows no exact view.
                val area = box.expanded()
                try {
                    _stops.value = repository.stopsIn(area).mapNotNull { location ->
                        location.stopId?.let { MapStop(it, location.name, location.point) }
                    }
                    loaded = area
                } catch (_: RoutingException) {
                    // Keep the stops on the map; the next camera move tries again.
                }
            }
        }
    }

    /** Called each time the camera stops. */
    fun onCameraIdle(box: BoundingBox, zoom: Double) {
        view.value = View(box, zoom)
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 500L

        /** About 5 km: a phone at [MAP_STOPS_MIN_ZOOM] shows much less, so this only stops odd cases such as tablets. */
        private const val MAX_SPAN = 0.05

        /** About 1 km at mid latitudes. */
        private const val GRID = 0.01

        private fun BoundingBox.contains(other: BoundingBox) =
            other.south >= south && other.north <= north && other.west >= west && other.east <= east

        private fun BoundingBox.expanded() = BoundingBox(
            south = floor(south / GRID) * GRID,
            west = floor(west / GRID) * GRID,
            north = ceil(north / GRID) * GRID,
            east = ceil(east / GRID) * GRID,
        )
    }
}
