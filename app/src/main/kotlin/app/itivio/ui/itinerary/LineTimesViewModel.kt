package app.itivio.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val journey = MutableStateFlow<Pair<Journey?, Instant?>>(null to null)

    // Kept across collector restarts, so a return from the background does not blank the times.
    private data class LegKey(
        val index: Int, val from: String?, val line: Line, val direction: String?,
        val toStop: String?, val toStation: String?,
    )
    private var shownFor: List<LegKey>? = null
    private var shown = emptyMap<Int, List<Departure>>()

    /** An eligible leg has no entry until the first round completes, including an empty or failed result. All legs get their entry together. */
    val times: StateFlow<Map<Int, List<Departure>>> = journey
        .transformLatest { (journey, notBefore) ->
            val legs = journey?.legs.orEmpty().mapIndexedNotNull { index, leg ->
                (leg as? Leg.Transit)?.takeIf { it.from.stopId != null }?.let { index to it }
            }
            val key = legs.map { (index, leg) -> LegKey(index, leg.from.stopId, leg.line, leg.directionId, leg.to.stopId, leg.to.stationId) }
            if (key != shownFor) {
                shownFor = key
                shown = emptyMap()
            }
            emit(shown)
            if (journey == null || legs.isEmpty()) return@transformLatest
            while (true) {
                val now = clock()
                // All legs at once: the lists appear together, after the slowest request instead of the sum.
                val loaded = coroutineScope {
                    legs.map { (index, leg) ->
                        async { index to load(leg, readyAt(journey, index, maxOf(now, notBefore ?: now))) }
                    }.awaitAll()
                }
                shown = loaded.associate { (index, times) -> index to (times ?: shown[index].orEmpty()) }
                emit(shown)
                delay(REFRESH_MILLIS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun show(journey: Journey, notBefore: Instant? = null) {
        this.journey.value = journey to notBefore
    }

    private fun readyAt(journey: Journey, index: Int, now: Instant): Instant {
        val previous = (0 until index).lastOrNull { journey.legs[it] is Leg.Transit }
        val walks = journey.legs.subList(previous?.plus(1) ?: 0, index)
        if (previous != null) {
            return maxOf(now, walks.fold(journey.legs[previous].end.best) { time, leg -> time.plus(leg.duration) })
        }
        // A planner can move the initial walk later to avoid a wait. Do not inherit that wait.
        return walks.fold(now) { time, leg ->
            val remaining = if (now > leg.start.best) Duration.between(now, leg.end.best).coerceAtLeast(Duration.ZERO) else leg.duration
            time.plus(remaining)
        }
    }

    private suspend fun load(leg: Leg.Transit, from: Instant): List<Departure>? = try {
        leg.from.stopId?.let { repository.lineDepartures(it, leg.line, leg.directionId, from, alightAt = leg.to) }
    } catch (_: RoutingException) {
        null
    }

    companion object {
        /** The same interval as the nearby departures. */
        const val REFRESH_MILLIS = 60_000L
    }
}
