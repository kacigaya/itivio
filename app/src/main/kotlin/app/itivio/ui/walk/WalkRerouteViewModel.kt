package app.itivio.ui.walk

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Leg
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkRoute
import java.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WalkRerouteViewModel(
    private val repository: RoutingRepository,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    data class Reroute(val key: Any, val leg: Leg.Street)

    private val _reroute = MutableStateFlow<Reroute?>(null)
    val reroute: StateFlow<Reroute?> = _reroute.asStateFlow()

    private var job: Job? = null
    private var lastRequest: Long? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun reroute(key: Any, leg: Leg.Street, from: GeoPoint, options: JourneyOptions) {
        if (job?.isActive == true) return
        val time = elapsedMillis()
        if (lastRequest?.let { time - it < MIN_INTERVAL.toMillis() } == true) return
        lastRequest = time
        val end = leg.to
        val request = JourneyRequest(
            from = Location(FROM_ID, "", LocationKind.ADDRESS, from),
            to = Location(end.stopId ?: TO_ID, end.name, if (end.stopId != null) LocationKind.STOP else LocationKind.ADDRESS, end.point, stopId = end.stopId),
            options = options,
        )
        job = viewModelScope.launch {
            try {
                val walk = repository.planJourneys(request).journeys.firstNotNullOfOrNull { journey ->
                    (journey.legs.singleOrNull() as? Leg.Street)
                        ?.takeIf { it.mode == TransitMode.WALK && WalkRoute.of(it.steps, it.geometry).lengthMeters > 0 }
                }
                if (walk != null) _reroute.value = Reroute(key, walk.copy(to = walk.to.copy(zone = walk.to.zone ?: end.zone)))
            } catch (_: RoutingException) {
            }
        }
    }

    companion object {
        val MIN_INTERVAL: Duration = Duration.ofSeconds(20)
        private const val FROM_ID = "walk-reroute-from"
        private const val TO_ID = "walk-reroute-to"
    }
}
