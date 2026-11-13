package app.itivio.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * The planned journey with the current times of its trips. Each minute while the itinerary is
 * visible, it asks for the trip of each transit leg that has not ended. A failed request keeps the
 * last times of that leg and shows no error: the planned times stay usable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripTrackerViewModel(
    private val repository: RoutingRepository,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val planned = MutableStateFlow<Journey?>(null)

    // Kept across collector restarts, so a return from the background does not drop the live times.
    private var shownFor: Journey? = null
    private var shown: Journey? = null

    val journey: StateFlow<Journey?> = planned
        .transformLatest { plan ->
            if (plan != shownFor) {
                shownFor = plan
                shown = plan
            }
            emit(shown)
            if (plan == null) return@transformLatest
            val legs = plan.legs.withIndex().filter { (_, leg) -> leg is Leg.Transit && leg.tripId != null }
            if (legs.isEmpty()) return@transformLatest
            while (true) {
                val now = clock()
                var current = shown ?: plan
                for ((index, leg) in legs) {
                    // A trip that ended a while ago cannot change the tracker any more.
                    if (current.legs[index].end.best < now.minus(ENDED_MARGIN)) continue
                    val trip = try {
                        repository.trip((leg as Leg.Transit).tripId ?: continue)
                    } catch (_: RoutingException) {
                        continue
                    }
                    current = current.withTrip(index, trip)
                }
                shown = current
                emit(current)
                delay(REFRESH_MILLIS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun show(journey: Journey) {
        planned.value = journey
    }

    companion object {
        /** The same interval as the line times. */
        const val REFRESH_MILLIS = 60_000L

        private val ENDED_MARGIN: Duration = Duration.ofMinutes(2)
    }
}
