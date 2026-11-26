package app.itivio.ui.explore

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.JourneyRequest
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.domain.SettingsRepository
import app.itivio.domain.Shortcut
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.attempt
import app.itivio.ui.common.closeTogether
import app.itivio.ui.planner.PlannerViewModel
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * The time to Home and to Work if the rider leaves now: from now to the first arrival. Off by
 * default, because it sends the position and both places. When on, it plans one journey to each
 * set shortcut while home is on screen, and again after a move of [MOVE_METERS], at most once every
 * [MIN_INTERVAL]. A failed request leaves out its time, and a time under a minute is left out too,
 * so standing at Home shows no "0 min".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutTimesViewModel(
    repository: RoutingRepository,
    userLocation: UserLocation,
    places: PlacesRepository,
    journeyOptions: JourneyOptionsRepository,
    settings: SettingsRepository,
    clock: Clock = Clock.systemUTC(),
    elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    /** The times of the last plan, so a move inside [MIN_INTERVAL] keeps them on the buttons. */
    private var planned: Map<Shortcut, Duration> = emptyMap()
    private var lastPlan: Long? = null
    private var lastTargets: Pair<List<Location>, JourneyOptions>? = null

    val times: StateFlow<Map<Shortcut, Duration>> =
        settings.shortcutTimes.catch { emit(false) }.distinctUntilChanged().flatMapLatest { on ->
            if (!on) return@flatMapLatest flowOf(emptyMap())
            combine(
                userLocation.point.distinctUntilChanged { a, b -> closeTogether(a, b, MOVE_METERS) },
                places.saved.map { saved -> Shortcut.entries.mapNotNull { shortcut -> saved.shortcut(shortcut)?.let { shortcut to it } } }
                    .distinctUntilChanged(),
                journeyOptions.options.catch { emit(JourneyOptions()) },
            ) { point, shortcuts, options -> Triple(point, shortcuts, options) }
                .mapLatest { (point, shortcuts, options) ->
                    if (point == null) {
                        planned = emptyMap()
                        return@mapLatest emptyMap()
                    }
                    val targets = shortcuts.map { it.second }
                    val now = elapsedMillis()
                    // Transitous is a community server: a move waits for the interval, but a new
                    // place or a new option plans at once.
                    if (targets to options == lastTargets && lastPlan?.let { now - it < MIN_INTERVAL.toMillis() } == true) {
                        return@mapLatest planned
                    }
                    lastPlan = now
                    lastTargets = targets to options
                    val from = Location(PlannerViewModel.MY_LOCATION_ID, "", LocationKind.PLACE, point)
                    planned = coroutineScope {
                        shortcuts.map { (shortcut, place) ->
                            async {
                                val at = clock.instant()
                                attempt { repository.planJourneys(JourneyRequest(from, place, options = options)) }
                                    .getOrNull()
                                    ?.journeys?.filterNot { it.isCancelled }?.minOfOrNull { it.arrival.best }
                                    ?.let { arrival -> shortcut to Duration.between(at, arrival) }
                                    ?.takeIf { (_, time) -> time >= MIN_TIME }
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }
                    planned
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        /** The same distance as the nearby departures. */
        const val MOVE_METERS = 150.0

        /** The same limit as the walk reroute, so a move does not re-plan on every fix. */
        val MIN_INTERVAL: Duration = Duration.ofSeconds(20)

        /** A rider who is already at Home sees no time, not "0 min". */
        val MIN_TIME: Duration = Duration.ofMinutes(1)
    }
}
