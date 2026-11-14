package app.itivio.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.RoutingException
import app.itivio.domain.RoutingRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

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

    fun selectDeparture(legIndex: Int, departure: Departure) {
        val baseline = original ?: return
        if (baseline.legs.getOrNull(legIndex) !is Leg.Transit) return
        val tripId = departure.tripId?.takeIf { it.isNotBlank() } ?: return
        if (departure.cancelled) return
        selectionJob?.cancel()
        selectionState.value = selectionState.value.copy(loadingLeg = legIndex, failedLeg = null)
        selectionJob = viewModelScope.launch {
            val trip = try {
                repository.trip(tripId)
            } catch (_: RoutingException) {
                selectionState.value = selectionState.value.copy(loadingLeg = null, failedLeg = legIndex)
                return@launch
            }
            ensureActive()
            val current = shown?.takeIf { shownFor == planned.value } ?: planned.value ?: return@launch
            var updated = current.withDeparture(legIndex, departure, trip)
            if (updated == null) {
                selectionState.value = selectionState.value.copy(loadingLeg = null, failedLeg = legIndex)
                return@launch
            }
            val originalLeg = baseline.legs[legIndex] as Leg.Transit
            if (tripId == originalLeg.tripId) {
                val restored = updated.legs.mapIndexed { index, leg ->
                    val originalWalk = baseline.legs.getOrNull(index) as? Leg.Street
                    if (leg is Leg.Street && originalWalk != null && index in legIndex - 1..legIndex + 1 &&
                        leg.from.stopId == originalWalk.from.stopId && leg.to.stopId == originalWalk.to.stopId &&
                        leg.from.track == originalWalk.from.track && leg.to.track == originalWalk.to.track
                    ) leg.copy(geometry = originalWalk.geometry, steps = originalWalk.steps) else leg
                }
                updated = updated.copy(legs = restored)
            }
            val changed = selectionState.value.changedLegs.let { if (tripId == originalLeg.tripId) it - legIndex else it + legIndex }
            val updatedLeg = updated.legs[legIndex] as Leg.Transit
            val platformChanged = originalLeg.from.stopId != updatedLeg.from.stopId || originalLeg.to.stopId != updatedLeg.to.stopId ||
                originalLeg.from.track != updatedLeg.from.track || originalLeg.to.track != updatedLeg.to.track
            val platforms = selectionState.value.platformChangedLegs.let { if (platformChanged) it + legIndex else it - legIndex }
            planned.value = updated
            selectionState.value = TrainSelectionState(changedLegs = changed, platformChangedLegs = platforms)
        }
    }

    fun resetTrain(legIndex: Int) {
        val leg = original?.legs?.getOrNull(legIndex) as? Leg.Transit ?: return
        selectDeparture(legIndex, Departure(leg.from, leg.line, leg.mode, leg.headsign, leg.start,
            leg.cancelled, leg.tripId, leg.directionId, leg.tripCode, leg.alerts))
    }

    companion object {
        /** The same interval as the line times. */
        const val REFRESH_MILLIS = 60_000L

        private val ENDED_MARGIN: Duration = Duration.ofMinutes(2)
    }
}
