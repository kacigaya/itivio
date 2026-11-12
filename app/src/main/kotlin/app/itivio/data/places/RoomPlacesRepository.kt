package app.itivio.data.places

import app.itivio.domain.GeoPoint
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.TransitMode
import app.itivio.domain.savedKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomPlacesRepository(
    private val dao: SavedPlaceDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlacesRepository {
    override val saved: Flow<SavedPlaces> = dao.observeAll().map { rows ->
        fun list(role: PlaceRole) = rows.filter { it.role == role }.map { it.toLocation() }
        SavedPlaces(
            home = list(PlaceRole.HOME).lastOrNull(),
            work = list(PlaceRole.WORK).lastOrNull(),
            favorites = list(PlaceRole.FAVORITE),
            recents = list(PlaceRole.RECENT).asReversed(),
        )
    }

    override suspend fun setFavorite(location: Location, favorite: Boolean) {
        if (favorite) dao.upsert(location.toEntity(PlaceRole.FAVORITE)) else dao.delete(PlaceRole.FAVORITE, location.savedKey)
    }

    override suspend fun setShortcut(shortcut: Shortcut, location: Location?) {
        val role = shortcut.role()
        if (location == null) dao.deleteAll(role) else dao.replace(location.toEntity(role))
    }

    override suspend fun addRecent(location: Location) =
        dao.upsertAndTrim(location.toEntity(PlaceRole.RECENT), PlacesRepository.MAX_RECENTS)

    override suspend fun clearRecents() = dao.deleteAll(PlaceRole.RECENT)

    private fun Location.toEntity(role: PlaceRole) = SavedPlaceEntity(
        role = role,
        key = savedKey,
        id = id,
        name = name,
        kind = kind.name,
        latitude = point.latitude,
        longitude = point.longitude,
        area = area,
        stopId = stopId,
        modes = modes.joinToString(",") { it.name },
        savedAt = clock(),
    )
}

private fun Shortcut.role(): PlaceRole = when (this) {
    Shortcut.HOME -> PlaceRole.HOME
    Shortcut.WORK -> PlaceRole.WORK
}

private fun SavedPlaceEntity.toLocation() = Location(
    id = id,
    name = name,
    kind = LocationKind.entries.firstOrNull { it.name == kind } ?: LocationKind.PLACE,
    point = GeoPoint(latitude, longitude),
    area = area,
    stopId = stopId,
    modes = modes.split(',').mapNotNull { mode -> TransitMode.entries.firstOrNull { it.name == mode } }.toSet(),
)
