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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey

internal class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    /** A value that this app version does not know falls back to the system theme. */
    override val theme: Flow<ThemeMode> = store.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[THEME] } ?: ThemeMode.SYSTEM
    }

    override suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override val disruptionAlerts: Flow<Boolean> = store.data.map { it[DISRUPTION_ALERTS] ?: false }

    override suspend fun setDisruptionAlerts(on: Boolean) {
        store.edit { it[DISRUPTION_ALERTS] = on }
    }

    override val severeAlertsOnly: Flow<Boolean> = store.data.map { it[SEVERE_ALERTS_ONLY] ?: true }

    override suspend fun setSevereAlertsOnly(on: Boolean) {
        store.edit { it[SEVERE_ALERTS_ONLY] = on }
    }

    override val notifiedAlerts: Flow<Set<String>> = store.data.map { it[NOTIFIED_ALERTS].orEmpty() }

    override suspend fun setNotifiedAlerts(keys: Set<String>) {
        store.edit { it[NOTIFIED_ALERTS] = keys }
    }

    override val shortcutTimes: Flow<Boolean> = store.data.map { it[SHORTCUT_TIMES] ?: false }

    override suspend fun setShortcutTimes(on: Boolean) {
        store.edit { it[SHORTCUT_TIMES] = on }
    }
}

private val THEME = stringPreferencesKey("theme")
private val DISRUPTION_ALERTS = booleanPreferencesKey("disruption_alerts")
private val SEVERE_ALERTS_ONLY = booleanPreferencesKey("severe_alerts_only")
private val NOTIFIED_ALERTS = stringSetPreferencesKey("notified_alerts")
private val SHORTCUT_TIMES = booleanPreferencesKey("shortcut_times")

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
