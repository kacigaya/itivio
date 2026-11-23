package app.itivio.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import app.itivio.domain.samePlatformAs
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

data class TrainSelectionState(
    val loadingLeg: Int? = null,
    val failedLeg: Int? = null,
    val changedLegs: Set<Int> = emptySet(),
    val platformChangedLegs: Set<Int> = emptySet(),
)

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
    private var original: Journey? = null
    private var selectionJob: Job? = null
    private val selectionState = MutableStateFlow(TrainSelectionState())
    val selection = selectionState.asStateFlow()

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
                // A trip that ended a while ago cannot change the tracker any more.
                val active = legs.filter { (index, _) -> current.legs[index].end.best >= now.minus(ENDED_MARGIN) }
                // All trips at once, so the live times land in one update.
                val trips = coroutineScope {
                    active.map { (index, leg) ->
                        async {
                            val tripId = (leg as Leg.Transit).tripId ?: return@async null
                            try {
                                index to repository.trip(tripId)
                            } catch (_: RoutingException) {
                                null
                            }
                        }
                    }.awaitAll()
                }
                trips.filterNotNull().forEach { (index, trip) -> current = current.withTrip(index, trip) }
                shown = current
                emit(current)
                delay(REFRESH_MILLIS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun show(journey: Journey) {
        if (original == journey) return
        selectionJob?.cancel()
        original = journey
        selectionState.value = TrainSelectionState()
        planned.value = journey
    }

    /**
     * Follows [departure] on the leg [legIndex]. Each later leg then takes the first train it can
     * still catch, or its planned train again when all trains before it are the planned ones and it
     * can still be caught.
     */
    fun selectDeparture(legIndex: Int, departure: Departure) {
        val baseline = original ?: return
        if (baseline.legs.getOrNull(legIndex) !is Leg.Transit) return
        if (departure.tripId.isNullOrBlank() || departure.cancelled) return
        selectionJob?.cancel()
        selectionState.value = selectionState.value.copy(loadingLeg = legIndex, failedLeg = null)
        selectionJob = viewModelScope.launch {
            var journey = apply(baseline, legIndex, departure) { shown?.takeIf { shownFor == planned.value } ?: planned.value }
                ?: run {
                    selectionState.value = selectionState.value.copy(loadingLeg = null, failedLeg = legIndex)
                    return@launch
                }
            var index = journey.nextTransit(legIndex)
            while (index != null) {
                selectionState.value = selectionState.value.copy(loadingLeg = index)
                val current = journey
                val connection = connection(baseline, current, index)
                if (connection != null && connection.tripId != (current.legs[index] as Leg.Transit).tripId) {
                    journey = apply(baseline, index, connection) { current } ?: current
                }
                index = journey.nextTransit(index)
            }
            selectionState.value = selectionState.value.copy(loadingLeg = null)
        }
    }

    /**
     * Puts the trip of [departure] on the leg [legIndex] of the journey that [current] gives after
     * the trip request, and shows the result. Null when the trip cannot replace the leg.
     */
    private suspend fun apply(baseline: Journey, legIndex: Int, departure: Departure, current: () -> Journey?): Journey? {
        val tripId = departure.tripId ?: return null
        val trip = try {
            repository.trip(tripId)
        } catch (_: RoutingException) {
            return null
        }
        currentCoroutineContext().ensureActive()
        var updated = current()?.withDeparture(legIndex, departure, trip) ?: return null
        val originalLeg = baseline.legs[legIndex] as Leg.Transit
        if (tripId == originalLeg.tripId) {
            val restored = updated.legs.mapIndexed { index, leg ->
                val originalWalk = baseline.legs.getOrNull(index) as? Leg.Street
                if (leg is Leg.Street && originalWalk != null && index in legIndex - 1..legIndex + 1 &&
                    leg.from.samePlatformAs(originalWalk.from) && leg.to.samePlatformAs(originalWalk.to)
                ) leg.copy(geometry = originalWalk.geometry, steps = originalWalk.steps) else leg
            }
            updated = updated.copy(legs = restored)
        }
        val state = selectionState.value
        val changed = if (tripId == originalLeg.tripId) state.changedLegs - legIndex else state.changedLegs + legIndex
        val updatedLeg = updated.legs[legIndex] as Leg.Transit
        val platformChanged = !originalLeg.from.samePlatformAs(updatedLeg.from) || !originalLeg.to.samePlatformAs(updatedLeg.to)
        val platforms = if (platformChanged) state.platformChangedLegs + legIndex else state.platformChangedLegs - legIndex
        planned.value = updated
        selectionState.value = state.copy(changedLegs = changed, platformChangedLegs = platforms)
        return updated
    }

    /**
     * The train the leg [index] of [journey] takes after a change before it: the planned train while
     * the trains before it are the planned ones and it can still be caught, else the first train
     * after the incoming train and the walk. Null when no such train is known; the leg then keeps its train.
     */
    private suspend fun connection(baseline: Journey, journey: Journey, index: Int): Departure? {
        val leg = journey.legs[index] as Leg.Transit
        val previous = (0 until index).last { journey.legs[it] is Leg.Transit }
        val ready = journey.legs.subList(previous + 1, index).fold(journey.legs[previous].end.best) { time, walk -> time.plus(walk.duration) }
        val planned = baseline.legs[index] as Leg.Transit
        if (selectionState.value.changedLegs.none { it < index } && planned.start.best >= ready) return planned.departure()
        val stopId = leg.from.stopId ?: return null
        val departures = try {
            repository.lineDepartures(stopId, leg.line, leg.directionId, maxOf(clock(), ready), alightAt = leg.to)
        } catch (_: RoutingException) {
            return null
        }
        return departures.firstOrNull { !it.cancelled && !it.tripId.isNullOrBlank() }
    }

    fun resetTrain(legIndex: Int) {
        val leg = original?.legs?.getOrNull(legIndex) as? Leg.Transit ?: return
        selectDeparture(legIndex, leg.departure())
    }

    private fun Leg.Transit.departure() =
        Departure(from, line, mode, headsign, start, cancelled, tripId, directionId, tripCode, alerts)

    private fun Journey.nextTransit(after: Int): Int? = (after + 1 until legs.size).firstOrNull { legs[it] is Leg.Transit }

    companion object {
        /** The same interval as the line times. */
        const val REFRESH_MILLIS = 60_000L

        private val ENDED_MARGIN: Duration = Duration.ofMinutes(2)
    }
}
