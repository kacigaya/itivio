package app.itivio.ui.common

import app.itivio.domain.GeoPoint
import app.itivio.domain.WalkRoute
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Latest device position, written by the map's location tracking. Kept in memory only and never
 * persisted, so the app holds no location history.
 */
class UserLocation {
    val point = MutableStateFlow<GeoPoint?>(null)
}

/** True when [a] and [b] are less than [meters] apart, or when both are unknown. */
internal fun closeTogether(a: GeoPoint?, b: GeoPoint?, meters: Double): Boolean =
    if (a == null || b == null) a == b else WalkRoute.distance(a, b) < meters
