package app.itivio.ui

import app.itivio.domain.Location
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.savedKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePlacesRepository(initial: SavedPlaces = SavedPlaces()) : PlacesRepository {
    override val saved = MutableStateFlow(initial)

    override suspend fun setFavorite(location: Location, favorite: Boolean) = saved.update { places ->
        val others = places.favorites.filterNot { it.savedKey == location.savedKey }
        places.copy(favorites = if (favorite) others + location else others)
    }

    override suspend fun setShortcut(shortcut: Shortcut, location: Location?) = saved.update {
        when (shortcut) {
            Shortcut.HOME -> it.copy(home = location)
            Shortcut.WORK -> it.copy(work = location)
        }
    }

    override suspend fun addRecent(location: Location) = saved.update { places ->
        val others = places.recents.filterNot { it.savedKey == location.savedKey }
        places.copy(recents = (listOf(location) + others).take(PlacesRepository.MAX_RECENTS))
    }

    override suspend fun clearRecents() = saved.update { it.copy(recents = emptyList()) }
}
