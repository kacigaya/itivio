package app.itivio.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.itivio.data.options.DataStoreJourneyOptionsRepository
import app.itivio.domain.JourneyOptions
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkingSpeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreJourneyOptionsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = TestScope()
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.root.resolve("settings.preferences_pb") }
    }
    private val repository by lazy { DataStoreJourneyOptionsRepository(store) }

    @Test
    fun `an empty store gives the defaults`() = scope.runTest {
        assertEquals(JourneyOptions(), repository.options.first())
    }

    @Test
    fun `saved options read back unchanged`() = scope.runTest {
        val options = JourneyOptions(
            excludedModes = setOf(TransitMode.BUS, TransitMode.AIRPLANE),
            maxTransfers = 0,
            wheelchair = true,
            walkingSpeed = WalkingSpeed.SLOW,
        )
        repository.save(options)
        assertEquals(options, repository.options.first())

        repository.save(JourneyOptions())
        assertEquals(JourneyOptions(), repository.options.first())
    }

    @Test
    fun `unknown stored values fall back to the defaults`() = scope.runTest {
        store.edit {
            it[stringSetPreferencesKey("journey_excluded_modes")] = setOf("TRAM", "HOVERCRAFT", "WALK")
            it[intPreferencesKey("journey_max_transfers")] = 99
            it[stringPreferencesKey("journey_walking_speed")] = "WARP"
        }

        assertEquals(JourneyOptions(excludedModes = setOf(TransitMode.TRAM)), repository.options.first())
    }

    @Test
    fun `a stored set that turns off all modes is ignored`() = scope.runTest {
        store.edit {
            it[stringSetPreferencesKey("journey_excluded_modes")] = JourneyOptions.SELECTABLE_MODES.map { mode -> mode.name }.toSet()
        }

        assertEquals(JourneyOptions(), repository.options.first())
    }
}
