package app.itivio.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Departure
import app.itivio.domain.RoutingRepository
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.toFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * The departure board of one stop. Refreshes once a minute while the board is visible. A failed
 * refresh keeps the last list; only the first load shows an error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopDeparturesViewModel(private val repository: RoutingRepository) : ViewModel() {
    private val stopId = MutableStateFlow<String?>(null)
    private val retries = MutableStateFlow(0)

    // Kept across collector restarts, so a return from the background does not blank the board.
    private var shownFor: String? = null
    private var shown: List<Departure>? = null

    val departures: StateFlow<LoadState<List<Departure>>> =
        combine(stopId.filterNotNull(), retries) { id, _ -> id }
            .transformLatest { id ->
                if (id != shownFor) {
                    shownFor = id
                    shown = null
                }
                while (true) {
                    shown?.let { emit(LoadState.Ready(it)) } ?: emit(LoadState.Loading)
                    val result = runCatching { repository.stopDepartures(id) }
                    val failure = result.exceptionOrNull()?.toFailure()
                    if (failure == null) {
                        shown = result.getOrThrow()
                        emit(LoadState.Ready(result.getOrThrow()))
                    } else if (shown == null) {
                        emit(LoadState.Failed(failure))
                        return@transformLatest
                    }
                    delay(REFRESH_MILLIS)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    fun show(stopId: String) {
        this.stopId.value = stopId
    }

    fun retry() {
        retries.value++
    }

    companion object {
        /** The same interval as the nearby departures. */
        const val REFRESH_MILLIS = 60_000L
    }
}
