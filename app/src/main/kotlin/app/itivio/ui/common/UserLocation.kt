package app.itivio.ui.common

import app.itivio.domain.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Latest device position, written by the map's location tracking. Kept in memory only and never
 * persisted, so the app holds no location history.
 */
class UserLocation {
    val point = MutableStateFlow<GeoPoint?>(null)
}
