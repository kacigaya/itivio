package app.itivio.ui

import android.database.sqlite.SQLiteFullException
import app.itivio.domain.Location
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.ui.search.SavedPlacesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `storage failures leave search usable`() = runTest(main.dispatcher) {
        val viewModel = SavedPlacesViewModel(BrokenPlacesRepository())
        viewModel.saved.launchIn(backgroundScope)
        val place = FakeRoutingRepository.location("Alexanderplatz")

        viewModel.addRecent(place)
        viewModel.toggleFavorite(place)
        viewModel.setShortcut(Shortcut.HOME, place)
        viewModel.clearRecents()
        advanceUntilIdle()

        assertEquals(SavedPlaces(), viewModel.saved.value)
    }

    @Test
    fun `a second toggle removes the shortcut`() = runTest(main.dispatcher) {
        val place = FakeRoutingRepository.location("Office")
        val repository = FakePlacesRepository()
        val viewModel = SavedPlacesViewModel(repository)
        viewModel.saved.launchIn(backgroundScope)

        viewModel.toggleShortcut(Shortcut.WORK, place)
        advanceUntilIdle()
        assertEquals(place, repository.saved.value.work)

        viewModel.toggleShortcut(Shortcut.WORK, place)
        advanceUntilIdle()
        assertEquals(null, repository.saved.value.work)
    }

    private class BrokenPlacesRepository : PlacesRepository {
        override val saved: Flow<SavedPlaces> = flow { throw SQLiteFullException() }
        override suspend fun setFavorite(location: Location, favorite: Boolean) = throw SQLiteFullException()
        override suspend fun setShortcut(shortcut: Shortcut, location: Location?) = throw SQLiteFullException()
        override suspend fun addRecent(location: Location) = throw SQLiteFullException()
        override suspend fun clearRecents() = throw SQLiteFullException()
    }
}
