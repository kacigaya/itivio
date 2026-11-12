package app.itivio.domain

import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class Shortcut { HOME, WORK }

/** Places the user keeps on the device. Each list holds a place at most once, by [savedKey]. */
data class SavedPlaces(
    val home: Location? = null,
    val work: Location? = null,
    /** In the order the user added them. */
    val favorites: List<Location> = emptyList(),
    /** Newest first. */
    val recents: List<Location> = emptyList(),
) {
    fun shortcut(shortcut: Shortcut): Location? = when (shortcut) {
        Shortcut.HOME -> home
        Shortcut.WORK -> work
    }

    fun isFavorite(location: Location): Boolean = favorites.any { it.savedKey == location.savedKey }

    fun isShortcut(shortcut: Shortcut, location: Location): Boolean = shortcut(shortcut)?.savedKey == location.savedKey
}

/**
 * Favorites, Home and Work, and recent searches. The data stays on the device; nothing here
 * goes to a network service. [saved] emits again after each change.
 */
interface PlacesRepository {
    val saved: Flow<SavedPlaces>

    suspend fun setFavorite(location: Location, favorite: Boolean)

    /** A null [location] removes the shortcut. */
    suspend fun setShortcut(shortcut: Shortcut, location: Location?)

    /** Puts [location] first in the recent searches. The oldest past [MAX_RECENTS] are removed. */
    suspend fun addRecent(location: Location)

    suspend fun clearRecents()

    companion object {
        const val MAX_RECENTS = 10
    }
}

/**
 * Identity of a saved place. Geocoding IDs can be blank or repeated, so places other than stops
 * also match by name and by the point rounded to about 1 m.
 */
val Location.savedKey: String
    get() = stopId?.let { "stop:$it" }
        ?: String.format(Locale.ROOT, "%s:%s:%s:%.5f,%.5f", kind, id, name, point.latitude, point.longitude)
