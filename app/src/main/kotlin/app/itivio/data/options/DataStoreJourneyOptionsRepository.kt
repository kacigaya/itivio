package app.itivio.data.options

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.WalkingSpeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DataStoreJourneyOptionsRepository(private val store: DataStore<Preferences>) : JourneyOptionsRepository {
    override val options: Flow<JourneyOptions> = store.data.map { it.toOptions() }

    override suspend fun save(options: JourneyOptions) {
        store.edit { preferences ->
            preferences[EXCLUDED_MODES] = options.excludedModes.map { it.name }.toSet()
            val maxTransfers = options.maxTransfers
            if (maxTransfers == null) preferences.remove(MAX_TRANSFERS) else preferences[MAX_TRANSFERS] = maxTransfers
            preferences[WHEELCHAIR] = options.wheelchair
            preferences[WALKING_SPEED] = options.walkingSpeed.name
        }
    }
}

private val EXCLUDED_MODES = stringSetPreferencesKey("journey_excluded_modes")
private val MAX_TRANSFERS = intPreferencesKey("journey_max_transfers")
private val WHEELCHAIR = booleanPreferencesKey("journey_wheelchair")
private val WALKING_SPEED = stringPreferencesKey("journey_walking_speed")

/** A value that this app version does not know falls back to its default. */
private fun Preferences.toOptions(): JourneyOptions {
    val excluded = this[EXCLUDED_MODES].orEmpty()
        .mapNotNull { name -> JourneyOptions.SELECTABLE_MODES.firstOrNull { it.name == name } }
        .toSet()
    return JourneyOptions(
        // The stored set cannot turn off all modes; see [JourneyOptions.withMode].
        excludedModes = excluded.takeUnless { it.containsAll(JourneyOptions.SELECTABLE_MODES) }.orEmpty(),
        maxTransfers = this[MAX_TRANSFERS]?.takeIf { it in 0..JourneyOptions.MAX_TRANSFER_LIMIT },
        wheelchair = this[WHEELCHAIR] ?: false,
        walkingSpeed = WalkingSpeed.entries.firstOrNull { it.name == this[WALKING_SPEED] } ?: WalkingSpeed.NORMAL,
    )
}
