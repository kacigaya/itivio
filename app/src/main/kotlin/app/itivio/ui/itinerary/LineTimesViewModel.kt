package app.itivio.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Upcoming departures of each transit leg's line at its boarding stop, by leg index. Refreshes
 * while the itinerary is visible. The times are an extra: a failed request keeps the last times
 * of that leg and shows no error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LineTimesViewModel(
    private val repository: RoutingRepository,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val journey = MutableStateFlow<Journey?>(null)

    // Kept across collector restarts, so a return from the background does not blank the times.
    private var shownFor: Journey? = null
    private var shown = emptyMap<Int, List<Departure>>()

    val times: StateFlow<Map<Int, List<Departure>>> = journey
        .transformLatest { journey ->
            if (journey != shownFor) {
                shownFor = journey
                shown = emptyMap()
            }
            emit(shown)
            val legs = journey?.legs.orEmpty().mapIndexedNotNull { index, leg ->
                (leg as? Leg.Transit)?.takeIf { it.from.stopId != null }?.let { index to it }
            }
            if (legs.isEmpty()) return@transformLatest
            while (true) {
                val now = clock()
                shown = legs.associate { (index, leg) -> index to (load(leg, now) ?: shown[index].orEmpty()) }
                emit(shown)
                delay(REFRESH_MILLIS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun show(journey: Journey) {
        this.journey.value = journey
    }

    /** The list starts at the planned departure, or at [now] when that time has passed. */
    private suspend fun load(leg: Leg.Transit, now: Instant): List<Departure>? = try {
        leg.from.stopId?.let { repository.lineDepartures(it, leg.line, leg.directionId, maxOf(now, leg.start.scheduled), alightAt = leg.to) }
    } catch (_: RoutingException) {
        null
    }

    companion object {
        /** The same interval as the nearby departures. */
        const val REFRESH_MILLIS = 60_000L
    }
}
