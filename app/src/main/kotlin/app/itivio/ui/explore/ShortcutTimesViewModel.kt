package app.itivio.ui.explore

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
 * set shortcut while home is on screen, and again after a move of [MOVE_METERS]. A failed request
 * leaves out its time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutTimesViewModel(
    repository: RoutingRepository,
    userLocation: UserLocation,
    places: PlacesRepository,
    journeyOptions: JourneyOptionsRepository,
    settings: SettingsRepository,
    clock: Clock = Clock.systemUTC(),
) : ViewModel() {
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
                    if (point == null) return@mapLatest emptyMap()
                    val from = Location(PlannerViewModel.MY_LOCATION_ID, "", LocationKind.PLACE, point)
                    coroutineScope {
                        shortcuts.map { (shortcut, place) ->
                            async {
                                val now = clock.instant()
                                runCatching { repository.planJourneys(JourneyRequest(from, place, options = options)) }.getOrNull()
                                    ?.journeys?.filterNot { it.isCancelled }?.minOfOrNull { it.arrival.best }
                                    ?.let { arrival -> shortcut to Duration.between(now, arrival).coerceAtLeast(Duration.ZERO) }
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        /** The same distance as the nearby departures. */
        const val MOVE_METERS = 150.0
    }
}
