package app.itivio.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.Location
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Favorites, Home and Work, and recent searches for the search view and the home sheet. Saved
 * places are optional: a storage failure, such as a full disk, must not stop search or routing.
 * A failed read shows no saved places, and a failed write changes nothing.
 */
class SavedPlacesViewModel(private val places: PlacesRepository) : ViewModel() {
    /** Null until the first read from storage, so the UI does not show empty shortcuts first. */
    val saved: StateFlow<SavedPlaces?> = places.saved
        .catch { emit(SavedPlaces()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite(location: Location) {
        val favorite = saved.value?.isFavorite(location) ?: false
        write { places.setFavorite(location, !favorite) }
    }

    /** Sets [shortcut] to [location], or removes it when it already holds [location]. */
    fun toggleShortcut(shortcut: Shortcut, location: Location) {
        val current = saved.value?.isShortcut(shortcut, location) ?: false
        write { places.setShortcut(shortcut, location.takeUnless { current }) }
    }

    fun setShortcut(shortcut: Shortcut, location: Location) {
        write { places.setShortcut(shortcut, location) }
    }

    fun addRecent(location: Location) {
        write { places.addRecent(location) }
    }

    fun clearRecents() {
        write { places.clearRecents() }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the lists keep their last state from storage.
            }
        }
    }
}
