package app.itivio.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.itivio.data.places.SavedPlaceDao
import app.itivio.domain.AppData
import app.itivio.domain.SettingsRepository
import app.itivio.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    /** A value that this app version does not know falls back to the system theme. */
    override val theme: Flow<ThemeMode> = store.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[THEME] } ?: ThemeMode.SYSTEM
    }

    override suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }
}

private val THEME = stringPreferencesKey("theme")

/**
 * The Room table of the saved places and the `settings` DataStore file. The file also holds the
 * journey options, so they go back to their defaults too.
 */
internal class LocalAppData(private val places: SavedPlaceDao, private val store: DataStore<Preferences>) : AppData {
    override suspend fun deleteAll() {
        places.deleteEverything()
        store.edit { it.clear() }
    }
}
