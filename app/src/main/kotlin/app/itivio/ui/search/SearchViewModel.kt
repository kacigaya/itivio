package app.itivio.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Location
import app.itivio.domain.RoutingRepository
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.common.toFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.milliseconds

/**
 * Autocomplete. Requests wait for typing to pause and need [MIN_QUERY] characters; a new query
 * cancels the request in flight. This keeps load on the shared Transitous service low.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: RoutingRepository,
    private val userLocation: UserLocation,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val retries = MutableStateFlow(0)

    val suggestions: StateFlow<LoadState<List<Location>>> = combine(
        _query.map { it.trim() }.distinctUntilChanged().debounce(DEBOUNCE),
        retries,
    ) { text, _ -> text }
        .transformLatest { text ->
            if (text.length < MIN_QUERY) {
                emit(LoadState.Idle)
                return@transformLatest
            }
            emit(LoadState.Loading)
            val result = runCatching { repository.searchLocations(text, userLocation.point.value) }
            emit(result.fold({ LoadState.Ready(it) }, { LoadState.Failed(it.toFailure()) }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Idle)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun retry() {
        retries.value++
    }

    companion object {
        const val MIN_QUERY = 2
        val DEBOUNCE = 300.milliseconds
    }
}
