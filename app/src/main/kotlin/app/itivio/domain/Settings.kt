package app.itivio.domain

import kotlinx.coroutines.flow.Flow

/** [SYSTEM] follows the light or dark setting of the device. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App settings. They stay on the device. */
interface SettingsRepository {
    val theme: Flow<ThemeMode>

    suspend fun setTheme(mode: ThemeMode)

    /** Shows the travel time to Home and Work on home. Off by default: it sends the position and both places. */
    val shortcutTimes: Flow<Boolean>

    suspend fun setShortcutTimes(on: Boolean)
}

/** All the data the app keeps on the device: saved places, journey options, and settings. */
fun interface AppData {
    /** Deletes the data. The app then starts like a new installation. */
    suspend fun deleteAll()
}
