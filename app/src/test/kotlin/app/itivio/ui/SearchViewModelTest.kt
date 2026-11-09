package app.itivio.ui

import app.itivio.domain.RoutingException
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.search.SearchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    // Lazy: the ViewModel reads Dispatchers.Main at construction, after the rule installs it.
    private val viewModel by lazy { SearchViewModel(repository, UserLocation()) }

    @Test
    fun `typing is debounced into one request`() = runTest(main.dispatcher) {
        viewModel.suggestions.launchIn(backgroundScope)
        listOf("A", "Al", "Ale", "Alex").forEach {
            viewModel.onQueryChange(it)
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("Alex"), repository.searches.map { it.first })
        val ready = viewModel.suggestions.value as LoadState.Ready
        assertEquals("Alex", ready.value.single().name)
    }

    @Test
    fun `short queries make no request`() = runTest(main.dispatcher) {
        viewModel.suggestions.launchIn(backgroundScope)
        viewModel.onQueryChange(" a ")
        advanceUntilIdle()

        assertTrue(repository.searches.isEmpty())
        assertEquals(LoadState.Idle, viewModel.suggestions.value)
    }

    @Test
    fun `failure can be retried with the same text`() = runTest(main.dispatcher) {
        viewModel.suggestions.launchIn(backgroundScope)
        repository.searchResult = { throw RoutingException.Offline() }
        viewModel.onQueryChange("Zoo")
        advanceUntilIdle()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.suggestions.value)

        repository.searchResult = { listOf(FakeRoutingRepository.location(it)) }
        viewModel.retry()
        runCurrent()
        advanceUntilIdle()
        assertTrue(viewModel.suggestions.value is LoadState.Ready)
        assertEquals(2, repository.searches.size)
    }
}
